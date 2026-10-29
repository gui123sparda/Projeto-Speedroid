#include "gps_vss.h"
#include <cassert>
#include <cstdint>

int main() {
    GpsVssState state;
    assert(!state.enabled);
    assert(state.update(3,1234,1000));
    assert(state.speed(1000)==123);
    assert(state.speed(2500)==123);
    assert(state.speed(2501)==0);
    assert(!state.update(3,5001,2600));
    assert(!state.update(2,400,2600));
    assert(state.update(1,0,2700));
    assert(state.enabled && state.speed(2700)==0);
    assert(state.update(3,0,2800));
    assert(state.speed(2800)==0);
    assert(state.update(0,0,2900));
    assert(!state.enabled);
    assert(state.update(3,600,UINT32_MAX-500));
    assert(state.speed(100)==60);
    assert(state.speed(2000)==0);
}
