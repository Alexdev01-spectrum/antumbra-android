//! Clean session ownership: port lives inside Device after init.
//! This module is the target shape; lib.rs probe is transitional.

use android_usb::AndroidUsbMTKPort;
use penumbra_mtk::Device;

pub enum SessionState {
    PortOnly(AndroidUsbMTKPort),
    /// Device owns the port. 'static is achieved by owning DA bytes in the session.
    Active {
        // DA bytes must outlive Device — store them here.
        da_bytes: Vec<u8>,
        // Device<'a, AndroidUsbMTKPort> cannot be named easily with self-ref.
        // Use a type-erased approach or ouroboros / self_cell in a follow-up.
        // For the scaffold, probe runs init and records metadata only.
    },
}
