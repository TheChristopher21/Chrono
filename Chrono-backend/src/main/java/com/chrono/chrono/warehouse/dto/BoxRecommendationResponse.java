package com.chrono.chrono.warehouse.dto;

import java.util.List;

public class BoxRecommendationResponse {

    public static class Alternative {
        private final String boxId;
        private final String boxName;
        private final int boxesRequired;
        private final double utilisation;
        private final double volumeCubicM;
        private final double maxWeightKg;

        public Alternative(String boxId, String boxName, int boxesRequired, double utilisation,
                            double volumeCubicM, double maxWeightKg) {
            this.boxId = boxId;
            this.boxName = boxName;
            this.boxesRequired = boxesRequired;
            this.utilisation = utilisation;
            this.volumeCubicM = volumeCubicM;
            this.maxWeightKg = maxWeightKg;
        }

        public String getBoxId() {
            return boxId;
        }

        public String getBoxName() {
            return boxName;
        }

        public int getBoxesRequired() {
            return boxesRequired;
        }

        public double getUtilisation() {
            return utilisation;
        }

        public double getVolumeCubicM() {
            return volumeCubicM;
        }

        public double getMaxWeightKg() {
            return maxWeightKg;
        }
    }

    private final String recommendedBoxId;
    private final String recommendedBoxName;
    private final int boxesRequired;
    private final double utilisation;
    private final double totalVolumeCubicM;
    private final double totalWeightKg;
    private final List<Alternative> alternatives;

    public BoxRecommendationResponse(String recommendedBoxId,
                                     String recommendedBoxName,
                                     int boxesRequired,
                                     double utilisation,
                                     double totalVolumeCubicM,
                                     double totalWeightKg,
                                     List<Alternative> alternatives) {
        this.recommendedBoxId = recommendedBoxId;
        this.recommendedBoxName = recommendedBoxName;
        this.boxesRequired = boxesRequired;
        this.utilisation = utilisation;
        this.totalVolumeCubicM = totalVolumeCubicM;
        this.totalWeightKg = totalWeightKg;
        this.alternatives = alternatives;
    }

    public String getRecommendedBoxId() {
        return recommendedBoxId;
    }

    public String getRecommendedBoxName() {
        return recommendedBoxName;
    }

    public int getBoxesRequired() {
        return boxesRequired;
    }

    public double getUtilisation() {
        return utilisation;
    }

    public double getTotalVolumeCubicM() {
        return totalVolumeCubicM;
    }

    public double getTotalWeightKg() {
        return totalWeightKg;
    }

    public List<Alternative> getAlternatives() {
        return alternatives;
    }
}
