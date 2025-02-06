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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
// projet nouveau
@Service
public class RewardsService {
	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// Proximity en miles
	private final int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private final int attractionProximityRange = 200;

	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;

	private final ExecutorService executor = Executors.newFixedThreadPool(16);


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
	/*public void calculateRewards(User user) {
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
	}*/
	/*
	public void calculateRewards(User user) {
		List<VisitedLocation> userLocations;
		List<Attraction> attractions;

		// Extraction des données sous verrou pour éviter les conflits de lecture
		synchronized (user) {
			userLocations = List.copyOf(user.getVisitedLocations());
			attractions = List.copyOf(gpsUtil.getAttractions());
		}

		// Liste des  recompense  pour chaque lieu visité
		List<CompletableFuture<Void>> rewardFutures = userLocations.stream()
				.map(visitedLocation ->
						CompletableFuture.supplyAsync(() -> findNearbyAttractions(visitedLocation, attractions), executor)
								.thenComposeAsync(nearbyAttractions -> processRewards(user, visitedLocation, nearbyAttractions), executor)
				)
				.collect(Collectors.toList());

		// Attendre la fin de tous les traitements
		CompletableFuture.allOf(rewardFutures.toArray(new CompletableFuture[0])).join();
	}
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
		});
	}

	private boolean isNearAttraction(VisitedLocation v, Attraction attraction) {
		return false;
	}

	// Trouver les attractions proches en parallèle
	private List<Attraction> findNearbyAttractions(VisitedLocation visitedLocation, List<Attraction> attractions) {
		return attractions.parallelStream()
				.filter(attraction -> nearAttraction(visitedLocation, attraction))
				.collect(Collectors.toList());
	}

	// Traitement des récompenses pour chaque attraction trouvée
	private CompletableFuture<Void> processRewards(User user, VisitedLocation visitedLocation, List<Attraction> attractions) {
		List<CompletableFuture<Void>> futures = attractions.stream()
				.map(attraction -> CompletableFuture.supplyAsync(() -> getReward(user, visitedLocation, attraction), executor)
						.thenAcceptAsync(reward -> {
							if (reward != null) {
								user.addUserReward(reward); // Suppression du synchronized (Assurez-vous que la structure de stockage est thread-safe)
							}
						}, executor))
				.collect(Collectors.toList());

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	// Vérification et génération des récompenses
	private UserReward getReward(User user, VisitedLocation visitedLocation, Attraction attraction) {
		boolean alreadyRewarded = user.getUserRewards().stream()
				.anyMatch(r -> r.attraction.attractionName.equals(attraction.attractionName));

		if (!alreadyRewarded) {
			int rewardPoints = getRewardPoints(attraction, user);
			return new UserReward(visitedLocation, attraction, rewardPoints);
		}
		return null;
	}
	//ICI


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

	public RewardCentral getRewardsCentral() {
		return rewardsCentral;
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

