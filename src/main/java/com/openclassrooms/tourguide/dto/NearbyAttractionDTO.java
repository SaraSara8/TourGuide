package com.openclassrooms.tourguide.dto;

import gpsUtil.location.Location;
import lombok.Getter;

@Getter
public class NearbyAttractionDTO {
    private String attractionName;
    private Location attractionLocation;
    private Location userLocation;
    private double distanceMiles;
    private int rewardPoints;

    public NearbyAttractionDTO(String attractionName, Location attractionLocation, Location userLocation, double distanceMiles, int rewardPoints) {
        this.attractionName = attractionName;
        this.attractionLocation = attractionLocation;
        this.userLocation = userLocation;
        this.distanceMiles = distanceMiles;
        this.rewardPoints = rewardPoints;

    }

    public NearbyAttractionDTO() {
    }

    public void setAttractionName(String attractionName) {
        this.attractionName = attractionName;
    }

    public void setAttractionLocation(Location attractionLocation) {
        this.attractionLocation = attractionLocation;
    }

    public void setUserLocation(Location userLocation) {
        this.userLocation = userLocation;
    }

    public void setDistanceMiles(double distanceMiles) {
        this.distanceMiles = distanceMiles;
    }

    public void setRewardPoints(int rewardPoints) {
        this.rewardPoints = rewardPoints;
    }
}
