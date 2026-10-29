package org.clonetuner.comm;

/** Blocking, bounded reads; implementations own and close their physical connection. */
public interface SerialIo {
    String description();
    void open() throws Exception;
    boolean isOpen();
    int read(byte[] buffer, int count);
    int write(byte[] buffer);
    void close();
}
