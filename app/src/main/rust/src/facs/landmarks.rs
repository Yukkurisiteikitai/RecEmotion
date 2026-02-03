// Indices based on MediaPipe Face Mesh (468 points)
// Ported from facs_core.py

pub const LEFT_EYE: &[usize] = &[33, 160, 158, 133, 153, 144];
pub const RIGHT_EYE: &[usize] = &[362, 385, 387, 263, 373, 380];
pub const BROW_LEFT_INNER: usize = 336;
pub const BROW_RIGHT_INNER: usize = 107;
pub const NOSE_ROOT: usize = 6;
pub const BROW_LEFT_OUTER: usize = 296;
pub const BROW_RIGHT_OUTER: usize = 66;
pub const LEFT_EYE_CORNER: usize = 33;
pub const RIGHT_EYE_CORNER: usize = 263;

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Point3D {
    pub x: f32,
    pub y: f32,
    pub z: f32,
}

impl Point3D {
    pub fn new(x: f32, y: f32, z: f32) -> Self {
        Self { x, y, z }
    }

    pub fn euclidean_dist(&self, other: &Point3D) -> f32 {
        let dx = self.x - other.x;
        let dy = self.y - other.y;
        let dz = self.z - other.z;
        (dx * dx + dy * dy + dz * dz).sqrt()
    }
}
