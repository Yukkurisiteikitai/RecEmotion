pub mod landmarks;
pub mod features;
pub mod calibration;

use std::collections::HashMap;

pub fn calculate_emotion(z_scores: &HashMap<String, f32>) -> String {
    // Current heuristic from emotion_analysis_system.py:
    // - AU4 < -2.0 => Anger/Focus
    // - AU1 > 2.0 and AU2 > 2.0 => Surprise
    // - EAR < -2.0 => Blink/Closed
    // - Default => Neutral
    
    // Check keys safely
    let au4 = *z_scores.get("AU4_Dist").unwrap_or(&0.0);
    let au1 = *z_scores.get("AU1_Dist").unwrap_or(&0.0);
    let au2 = *z_scores.get("AU2_Dist").unwrap_or(&0.0);
    let ear = *z_scores.get("EAR").unwrap_or(&0.0);

    // TODO: Make this threshold configurable in the future
    let threshold = 2.0; 

    if ear < -threshold && au4 > -1.0 {
        return "Happy".to_string(); // Added from demo_chat.py logic
    }
    
    if au4 < -threshold {
        return "Angry".to_string();
    }
    
    if au1 > threshold && au2 > threshold {
        return if ear > threshold { "Surprised".to_string() } else { "Curious".to_string() };
    }
    
    if au1 > threshold && au4 < 0.0 {
        return "Sad".to_string();
    }

    "Neutral".to_string()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::facs::landmarks::Point3D;
    use crate::facs::features::GeometricFeatureExtractor;
    use crate::facs::calibration::{StatisticalCalibrator, RobustStatistics};

    #[test]
    fn test_feature_extraction() {
        let mut landmarks = Vec::new();
        // Create dummy 468 points
        for _ in 0..468 {
            landmarks.push(Point3D::new(0.0, 0.0, 0.0));
        }
        
        let extractor = GeometricFeatureExtractor::new();
        let feats = extractor.extract(&landmarks);
        assert!(feats.is_some());
        let map = feats.unwrap();
        assert!(map.contains_key("EAR"));
        assert!(map.contains_key("AU1_Dist"));
    }

    #[test]
    fn test_robust_statistics() {
        // Odd length
        let v1 = vec![1.0, 3.0, 2.0]; // sorted: 1, 2, 3 -> median 2
        assert_eq!(RobustStatistics::compute_median(&v1), 2.0);

        // Even length
        let v2 = vec![1.0, 4.0, 3.0, 2.0]; // sorted: 1, 2, 3, 4 -> median 2.5
        assert_eq!(RobustStatistics::compute_median(&v2), 2.5);

        // MAD check
        // v1: median=2. Devs: |1-2|=1, |3-2|=1, |2-2|=0. Sorted: 0, 1, 1. Median Dev = 1.
        let stats = RobustStatistics::compute_robust_stats(&v1);
        assert_eq!(stats.median, 2.0);
        assert!((stats.sigma - 1.4826).abs() < 0.0001);
    }

    #[test]
    fn test_calibration_logic() {
        let mut cal = StatisticalCalibrator::new();
        
        let mut f1 = HashMap::new();
        f1.insert("A".to_string(), 10.0);
        
        let mut f2 = HashMap::new();
        f2.insert("A".to_string(), 12.0);

        let mut f3 = HashMap::new();
        f3.insert("A".to_string(), 11.0); 

        cal.add_sample(f1);
        cal.add_sample(f2);
        cal.add_sample(f3);
        
        assert!(cal.finalize_calibration());
        assert!(cal.is_calibrated);
        
        let stats = cal.stats.get("A").unwrap();
        assert_eq!(stats.median, 11.0);
        assert_eq!(cal.get_z_score("A", 11.0), 0.0); // (11-11)/sigma
    }
}
