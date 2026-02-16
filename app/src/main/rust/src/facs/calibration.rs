use std::collections::HashMap;

#[derive(Debug, Clone)]
pub struct FeatureStats {
    pub median: f32,
    pub sigma: f32,
}

/// Pure math implementation for rigorous testing
pub struct RobustStatistics;

impl RobustStatistics {
    /// Computes the median of a slice.
    /// Matches NumPy behavior: averages the two middle elements if len is even.
    pub fn compute_median(values: &[f32]) -> f32 {
        if values.is_empty() {
            return 0.0;
        }
        let mut sorted = values.to_vec();
        sorted.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));

        let len = sorted.len();
        if len % 2 == 1 {
            sorted[len / 2]
        } else {
            let mid1 = sorted[len / 2 - 1];
            let mid2 = sorted[len / 2];
            (mid1 + mid2) / 2.0
        }
    }

    /// Computes Median and Sigma (from MAD) using constant 1.4826.
    pub fn compute_robust_stats(values: &[f32]) -> FeatureStats {
        let median = Self::compute_median(values);
        
        // Calculate Absolute Deviations
        let abs_devs: Vec<f32> = values.iter()
            .map(|&v| (v - median).abs())
            .collect();
            
        let mad = Self::compute_median(&abs_devs);
        
        let mut sigma = 1.4826 * mad;
        if sigma < 1e-6 {
            sigma = 1e-6;
        }

        FeatureStats { median, sigma }
    }
}

#[derive(Debug, Clone)]
pub struct StatisticalCalibrator {
    pub samples: Vec<HashMap<String, f32>>,
    pub stats: HashMap<String, FeatureStats>,
    pub is_calibrated: bool,
}

impl StatisticalCalibrator {
    pub fn new() -> Self {
        Self {
            samples: Vec::new(),
            stats: HashMap::new(),
            is_calibrated: false,
        }
    }

    pub fn add_sample(&mut self, feats: HashMap<String, f32>) {
        self.samples.push(feats);
    }

    pub fn finalize_calibration(&mut self) -> bool {
        if self.samples.is_empty() {
            return false;
        }

        // Identify all keys present (assume consistent keys)
        if let Some(first) = self.samples.first() {
            let keys: Vec<String> = first.keys().cloned().collect();

            for k in keys {
                let vals: Vec<f32> = self.samples.iter()
                    .filter_map(|s| s.get(&k).cloned())
                    .collect();
                
                if !vals.is_empty() {
                    let stats = RobustStatistics::compute_robust_stats(&vals);
                    self.stats.insert(k, stats);
                }
            }
        }

        self.is_calibrated = !self.stats.is_empty();
        
        if self.is_calibrated {
            println!("Rust: Calibration Finalized. Stats: {:?}", self.stats);
        }
        
        self.is_calibrated
    }

    pub fn get_z_score(&self, key: &str, value: f32) -> f32 {
        if !self.is_calibrated {
            return 0.0;
        }
        if let Some(stat) = self.stats.get(key) {
            (value - stat.median) / stat.sigma
        } else {
            0.0
        }
    }
}
