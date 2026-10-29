package org.clonetuner.comm;

import org.clonetuner.ini.IniModel;
import org.clonetuner.model.Ecu;
import org.clonetuner.model.TriggerCapture;

import java.util.Map;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Owns the active {@link Link}: performs the initial page download, keeps the realtime channel
 * block fresh on a background thread and flushes dirty pages on burn.
 */
public final class Transport {

    private final Ecu ecu;
    private final IniModel ini;
    private volatile Link link;
    private final Object ioLock = new Object();
    private volatile Thread poller;
    private volatile boolean polling;
    private Consumer<String> logger = s -> {
    };

    private volatile TriggerCapture.Mode triggerMode;
    private volatile TriggerCapture triggerCapture;
    private volatile String triggerError;
    private volatile long triggerBlocks;
    private long lastTriggerRead;
    public boolean triggerLoggerActive() { return triggerMode!=null; }
    public TriggerCapture.Mode triggerLoggerMode() { return triggerMode; }
    public TriggerCapture triggerCapture() { return triggerCapture; }
    public String triggerLoggerError() { return triggerError; }
    public long triggerLoggerBlocks() { return triggerBlocks; }
    public boolean supportsTriggerLogger(TriggerCapture.Mode mode) {
        Link active=link; return active!=null && active.supportsTriggerLogger(mode);
    }
    public void startTriggerLogger(TriggerCapture.Mode mode) {
        synchronized(ioLock) {
            if(!isConnected()) throw new IllegalStateException("Conecte uma ECU ou inicie a demonstração");
            requireLoggerStopped();
            if(!link.supportsTriggerLogger(mode)) throw new UnsupportedOperationException("O INI atual não oferece este logger");
            triggerCapture=null; triggerBlocks=0; triggerError=null; lastTriggerRead=0;
            // Keep the requested mode for cleanup even if its acknowledgement is lost.
            triggerMode=mode;
            try { link.startTriggerLogger(mode); }
            catch(RuntimeException error) {
                triggerError=error.getMessage();
                disconnect();
                throw error;
            }
            logger.accept("Trigger logger started: "+mode);
        }
    }
    public void stopTriggerLogger() {
        synchronized(ioLock) {
            TriggerCapture.Mode mode=triggerMode;
            if(mode==null) return;
            triggerMode=null;
            try { if(isConnected()) link.stopTriggerLogger(mode); }
            catch(RuntimeException error) {
                triggerError="Falha ao encerrar logger: "+error.getMessage();
                link.disconnect(); stopPolling(); ecu.channels().clear();
                throw error;
            }
            logger.accept("Trigger logger stopped");
        }
    }
    private void requireLoggerStopped() {
        if(triggerLoggerActive()) throw new IllegalStateException("Pare o tooth/composite logger antes de ler, gravar ou enviar comandos à ECU");
    }
    private void readTriggerIfReady(Link active) {
        TriggerCapture.Mode mode=triggerMode;
        if(mode==null) return;
        long now=System.nanoTime()/1000000;
        if(now-lastTriggerRead<250) return;
        var definition=TriggerLoggerProtocol.definition(ini,mode);
        if(!ecu.test(definition.readyCondition)) return;
        TriggerCapture block=active.readTriggerLogger(mode);
        if(block.mode()!=mode) throw new IllegalStateException("Modo de captura não corresponde ao solicitado");
        triggerCapture=block; triggerBlocks++; lastTriggerRead=now;
    }

    public Transport(Ecu ecu) {
        this.ecu = ecu;
        this.ini = ecu.ini();
    }

    public void logger(Consumer<String> logger) {
        this.logger = logger;
    }

    public Link link() {
        return link;
    }

    public boolean isConnected() {
        Link active = link;
        return active != null && active.isConnected();
    }

    public boolean isSimulator() {
        Link active = link;
        return active != null && active.isSimulator();
    }

    public String signature() {
        synchronized (ioLock) { return link == null ? "" : link.signature(); }
    }

    public String version() {
        synchronized (ioLock) { return link == null ? "" : link.version(); }
    }

    public String description() {
        Link active = link;
        return active == null ? "Not connected" : active.displayName();
    }

    public void connect(Link newLink) throws Exception {
        synchronized (ioLock) {
            disconnect();
            link = newLink;
            triggerCapture=null; triggerError=null; triggerBlocks=0;
            try {
                if (link.isSimulator() && link instanceof SimLink sim) {
                    sim.loadPages(ecu.allPages());
                }
                if (!link.connect()) {
                    throw new IllegalStateException("Connection refused");
                }
                String sig = link.signature();
                if (!link.isSimulator() && (sig == null || !sig.equalsIgnoreCase(ini.signature))) {
                    throw new IllegalStateException("Firmware signature '" + sig + "' does not match INI '" + ini.signature + "'");
                }
                logger.accept("Connected to " + link.displayName() + " (" + sig + ")");
                if (ecu.isDirty() && !link.isSimulator()) {
                    logger.accept("Local tune has pending changes; ECU pages were not downloaded");
                } else {
                    downloadPages();
                }
                startPolling();
            } catch (Exception ex) {
                disconnect();
                throw ex;
            }
        }
    }

    public void disconnect() {
        synchronized (ioLock) {
            stopPolling();
            if (link != null) {
                try { stopTriggerLogger(); } catch(RuntimeException error) { logger.accept("Logger cleanup: "+error.getMessage()); }
                link.disconnect();
                logger.accept("Disconnected");
                link = null;
            }
            ecu.channels().clear();
        }
    }

    /** Reads every page into a temporary buffer, then replaces the tune only after all reads succeed. */
    public void downloadPages() {
        synchronized (ioLock) {
            requireLoggerStopped();
            if (!isConnected()) throw new IllegalStateException("No controller connected");
            int count = Math.max(1, ini.nPages);
            byte[][] incoming = new byte[count][];
            for (int p = 1; p <= count; p++) {
                byte[] data = link.readPage(p);
                if (data == null || data.length < ecu.page(p).length) {
                    throw new IllegalStateException("Incomplete page " + p + " download");
                }
                incoming[p - 1] = data;
            }
            for (int p = 1; p <= count; p++) ecu.replacePage(p, incoming[p - 1]);
            ecu.markClean();
        }
    }

    private void startPolling() {
        Link active = link;
        polling = true;
        poller = new Thread(() -> {
            while (polling && Thread.currentThread() == poller && active == link) {
                try {
                    synchronized (ioLock) {
                        if (!polling || Thread.currentThread() != poller || active != link) break;
                        Map<String, Double> values = active.readChannels();
                        if (values != null && !values.isEmpty()) {
                            ecu.updateChannels(values);
                            readTriggerIfReady(active);
                        }
                    }
                    Thread.sleep(Math.max(20, Math.min(200, 1000 / 20)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (RuntimeException e) {
                    logger.accept("Realtime read failed: " + e.getMessage());
                    synchronized (ioLock) {
                        if (active == link && Thread.currentThread() == poller) {
                            if(triggerMode!=null) triggerError="Captura interrompida: "+e.getMessage();
                            try { stopTriggerLogger(); } catch(RuntimeException ignored) { }
                            ecu.channels().clear();
                            stopPolling();
                            active.disconnect();
                        }
                    }
                    break;
                }
            }
        }, "ecu-realtime");
        poller.setDaemon(true);
        poller.start();
    }

    private void stopPolling() {
        polling = false;
        // Do not interrupt an in-flight serial response: its remaining bytes belong to that request.
        poller = null;
    }

    /** Writes every dirty page and burns it to flash. */
    public void burnAll() {
        synchronized (ioLock) {
            burnPages(java.util.stream.IntStream.range(0, ini.nPages).boxed().collect(java.util.stream.Collectors.toList()));
        }
    }

    public void burnDirty() {
        synchronized (ioLock) {
            burnPages(ecu.dirtyPages().stream().filter(p -> p >= 0).sorted().collect(java.util.stream.Collectors.toList()));
        }
    }

    private void burnPages(List<Integer> pageIndexes) {
        requireLoggerStopped();
        if (!isConnected()) throw new IllegalStateException("No controller connected; changes remain pending");
        for (int index : pageIndexes) {
            int page = index + 1;
            byte[] sent = ecu.page(page).clone();
            String phase = "write";
            try {
                logger.accept("Writing page " + page + " (" + sent.length + " bytes)");
                link.writePage(page, sent);
                phase = "readback";
                if (!Arrays.equals(sent, link.readPage(page))) {
                    throw new IllegalStateException("Controller RAM does not match the transmitted page");
                }
                phase = "burn acknowledgement";
                link.burn(page);
                if (Arrays.equals(sent, ecu.page(page))) ecu.markPageClean(index);
                logger.accept("Page " + page + " verified and burn acknowledged");
            } catch (RuntimeException e) {
                ecu.markDirty(index);
                String message = "Page " + page + " failed during " + phase + ": " + e.getMessage();
                logger.accept(message);
                throw new IllegalStateException(message, e);
            }
        }
        logger.accept("Burn complete");
    }

    public void sendCommand(String name) {
        synchronized (ioLock) {
            requireLoggerStopped();
            if (link == null || !link.isConnected()) {
                throw new IllegalStateException("No controller connected; command " + name + " was not sent");
            }
            List<String> commands = ini.controllerCommands.get(name);
            if (commands == null || commands.stream().allMatch(c -> c == null || c.isBlank())) {
                throw new IllegalArgumentException("Unknown or empty controller command: " + name);
            }
            try {
                link.sendCommand(name, commands);
                logger.accept("Sent " + name);
            } catch (RuntimeException e) {
                String message = "Command " + name + " failed: " + e.getMessage();
                logger.accept(message);
                throw new IllegalStateException(message, e);
            }
        }
    }
}
