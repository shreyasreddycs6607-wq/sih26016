"""Which device is wired in. Mirrors sih26016-backend's
app/integrations/providers.py: a factory per mode, selected by one config
value, so main.py never branches on MFS100_MODE itself."""

from device.base import DeviceError, FingerprintDevice
from device.mock import MockFingerprintDevice

REGISTRY = {
    "mock": MockFingerprintDevice,
}


def get_device(mode: str) -> FingerprintDevice:
    mode = (mode or "mock").strip().lower()
    if mode == "real":
        # Imported lazily: it loads a native DLL at construction time, which
        # should never happen just because something imported this module —
        # only when a real device was actually asked for.
        from device.mfs100 import MFS100Device

        return MFS100Device()

    device_cls = REGISTRY.get(mode)
    if device_cls is None:
        raise ValueError(f"Unknown MFS100_MODE '{mode}' — use 'mock' or 'real'.")
    return device_cls()


__all__ = ["DeviceError", "FingerprintDevice", "get_device"]
