package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RewardsService {
	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// Proximity en miles
	private final int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private final int attractionProximityRange = 200;

	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}

	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	/**
	 * Calcule les récompenses pour un utilisateur donné.
	 * On verrouille l'utilisateur pour éviter la ConcurrentModificationException.
	 */
	public void calculateRewards(User user) {
		synchronized (user) {
			// Récupération de la liste sous le verrou
			List<VisitedLocation> userLocations = user.getVisitedLocations();
			List<Attraction> attractions = gpsUtil.getAttractions();

			// Double boucle : positions visitées x attractions
			for (VisitedLocation visitedLocation : userLocations) {
				for (Attraction attraction : attractions) {
					// Vérifie si on n'a pas déjà une reward pour cette attraction
					boolean alreadyRewarded = user.getUserRewards().stream()
							.anyMatch(r -> r.attraction.attractionName.equals(attraction.attractionName));

					if (!alreadyRewarded) {
						// Vérifier la proximité
						if (nearAttraction(visitedLocation, attraction)) {
							// Ajouter la reward
							int rewardPoints = getRewardPoints(attraction, user);
							user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
						}
					}
				}
			}
		}
	}

	/**
	 * Vérifie si l'attraction se trouve dans un rayon <proximityBuffer> miles de visitedLocation.
	 */
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) <= proximityBuffer;
	}

	/**
	 * Récupère les points de récompense depuis la librairie RewardCentral.
	 */
	public int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

	/**
	 * Indique si l'attraction est dans un rayon de <attractionProximityRange> miles.
	 */
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return (getDistance(attraction, location) <= attractionProximityRange);
	}

	/**
	 * Calcule la distance en miles (approx. loi des cosinus).
	 */
	public double getDistance(Location loc1, Location loc2) {
		double lat1 = Math.toRadians(loc1.latitude);
		double lon1 = Math.toRadians(loc1.longitude);
		double lat2 = Math.toRadians(loc2.latitude);
		double lon2 = Math.toRadians(loc2.longitude);

		double angle = Math.acos(
				Math.sin(lat1) * Math.sin(lat2)
						+ Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2)
		);

		double nauticalMiles = 60 * Math.toDegrees(angle);
		return STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
	}
}
