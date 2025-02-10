package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rewardCentral.RewardCentral;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class RewardsService {
	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// Proximity en miles
	private final int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private final int attractionProximityRange = 200;

	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;

	// Utilisation d'un pool de threads fixe avec un nombre limité de threads (500 ici)
	private final ExecutorService executor = Executors.newFixedThreadPool(380);

	private Logger logger = LoggerFactory.getLogger(RewardsService.class);

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
	 * On itère sur la liste des attractions et, si une visite est proche (selon isNearAttraction),
	 * on ajoute la récompense correspondante à l'utilisateur.
	 */
	public CompletableFuture<Void> calculateRewardsAsync(User user) {
		return CompletableFuture.runAsync(() -> {
			List<Attraction> attractions = gpsUtil.getAttractions();
			for (Attraction attraction : attractions) {
				if (user.getVisitedLocations().stream().anyMatch(v -> isNearAttraction(v, attraction))) {
					int rewardPoints = rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
					user.addUserReward(new UserReward(user.getLastVisitedLocation(), attraction, rewardPoints));
				}
			}
		}, executor);
	}

	private boolean isNearAttraction(VisitedLocation v, Attraction attraction) {
		return getDistance(attraction, v.location) <= proximityBuffer;
	}

	private List<Attraction> findNearbyAttractions(VisitedLocation visitedLocation, List<Attraction> attractions) {
		return attractions.parallelStream()
				.filter(attraction -> nearAttraction(visitedLocation, attraction))
				.collect(Collectors.toList());
	}

	private CompletableFuture<Void> processRewards(User user, VisitedLocation visitedLocation, List<Attraction> attractions) {
		List<CompletableFuture<Void>> futures = attractions.stream()
				.map(attraction -> CompletableFuture.supplyAsync(() -> getReward(user, visitedLocation, attraction), executor)
						.thenAcceptAsync(reward -> {
							if (reward != null) {
								user.addUserReward(reward);
							}
						}, executor))
				.collect(Collectors.toList());
		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	private UserReward getReward(User user, VisitedLocation visitedLocation, Attraction attraction) {
		boolean alreadyRewarded = user.getUserRewards().stream()
				.anyMatch(r -> r.attraction.attractionName.equals(attraction.attractionName));
		if (!alreadyRewarded) {
			int rewardPoints = getRewardPoints(attraction, user);
			return new UserReward(visitedLocation, attraction, rewardPoints);
		}
		return null;
	}

	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) <= proximityBuffer;
	}

	public int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

	public RewardCentral getRewardsCentral() {
		return rewardsCentral;
	}

	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return (getDistance(attraction, location) <= attractionProximityRange);
	}

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