use std::collections::HashMap;
use crate::facs::landmarks::*;

pub struct GeometricFeatureExtractor;

impl GeometricFeatureExtractor {
    pub fn new() -> Self {
        Self
    }

    pub fn extract(&self, landmarks: &[Point3D]) -> Option<HashMap<String, f32>> {
        if landmarks.len() < 468 {
            return None;
        }

        let mut feats = HashMap::new();

        // 0. Calculate Scale Factor (IPD)
        let ipd = self.get_ipd_scale(landmarks);

        // 1. EAR (Eye Aspect Ratio) - No IPD normalization
        let l_ear = self.eye_aspect_ratio(landmarks, LEFT_EYE);
        let r_ear = self.eye_aspect_ratio(landmarks, RIGHT_EYE);
        feats.insert("EAR".to_string(), (l_ear + r_ear) / 2.0);

        // 2. Brow Inner Height (AU1) - Normalized
        let l_brow_in = landmarks[BROW_LEFT_INNER].euclidean_dist(&landmarks[NOSE_ROOT]);
        let r_brow_in = landmarks[BROW_RIGHT_INNER].euclidean_dist(&landmarks[NOSE_ROOT]);
        feats.insert("AU1_Dist".to_string(), ((l_brow_in + r_brow_in) / 2.0) / ipd);

        // 3. Brow Outer Height (AU2) - Normalized
        let l_brow_out = landmarks[BROW_LEFT_OUTER].euclidean_dist(&landmarks[NOSE_ROOT]);
        let r_brow_out = landmarks[BROW_RIGHT_OUTER].euclidean_dist(&landmarks[NOSE_ROOT]);
        feats.insert("AU2_Dist".to_string(), ((l_brow_out + r_brow_out) / 2.0) / ipd);

        // 4. Brow Lowerer (AU4) - Normalized
        let d_brows = landmarks[BROW_LEFT_INNER].euclidean_dist(&landmarks[BROW_RIGHT_INNER]);
        feats.insert("AU4_Dist".to_string(), d_brows / ipd);

        Some(feats)
    }

    fn get_ipd_scale(&self, landmarks: &[Point3D]) -> f32 {
        let p1 = &landmarks[LEFT_EYE_CORNER];
        let p2 = &landmarks[RIGHT_EYE_CORNER];
        let dist = p1.euclidean_dist(p2);
        if dist < 1.0 { 1.0 } else { dist }
    }

    fn eye_aspect_ratio(&self, landmarks: &[Point3D], eye_indices: &[usize]) -> f32 {
        let p: Vec<&Point3D> = eye_indices.iter().map(|&i| &landmarks[i]).collect();

        // Vertical distances
        let v1 = p[1].euclidean_dist(p[5]);
        let v2 = p[2].euclidean_dist(p[4]);
        
        // Horizontal distance
        let h = p[0].euclidean_dist(p[3]);

        if h == 0.0 {
            0.0
        } else {
            (v1 + v2) / (2.0 * h)
        }
    }
}
