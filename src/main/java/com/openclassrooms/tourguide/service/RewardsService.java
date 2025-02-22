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
	private final ExecutorService executor = Executors.newFixedThreadPool(200);

	private Logger logger = LoggerFactory.getLogger(RewardsService.class);

	/**
	 * Constructeur qui injecte les services nécessaires.
	 */
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}

	/**
	 * Permet de définir un rayon de proximité personnalisé pour la détection des attractions.
	 */
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	/**
	 * Réinitialise le rayon de proximité à sa valeur par défaut.
	 */
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	/**
	 * Calcule les récompenses pour un utilisateur de manière asynchrone.
	 * Pour chaque attraction, on vérifie si l'utilisateur est à proximité et on lui attribue une récompense si nécessaire.
	 */
	public CompletableFuture<Void> calculateRewardsAsync(User user) {
		return CompletableFuture.runAsync(() -> {
			List<Attraction> attractions = gpsUtil.getAttractions(); // Récupère toutes les attractions
			for (Attraction attraction : attractions) {
				// Vérifie si l'utilisateur a visité un lieu proche de l'attraction
				if (user.getVisitedLocations().stream().anyMatch(v -> isNearAttraction(v, attraction))) {
					int rewardPoints = rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
					user.addUserReward(new UserReward(user.getLastVisitedLocation(), attraction, rewardPoints));
				}
			}
		}, executor); // Exécute la tâche en parallèle en utilisant le pool de threads
	}

	/**
	 * Vérifie si une localisation visitée est proche d'une attraction en comparant les distances.
	 */
	private boolean isNearAttraction(VisitedLocation v, Attraction attraction) {
		return getDistance(attraction, v.location) <= proximityBuffer;
	}

	/**
	 * Trouve les attractions proches d'une localisation visitée.
	 * Cette méthode utilise `parallelStream` pour accélérer le filtrage en utilisant plusieurs threads.
	 */
	private List<Attraction> findNearbyAttractions(VisitedLocation visitedLocation, List<Attraction> attractions) {
		return attractions.parallelStream()
				.filter(attraction -> nearAttraction(visitedLocation, attraction))
				.collect(Collectors.toList());
	}

	/**
	 * Traite les récompenses pour un utilisateur et une liste d'attractions proches de manière asynchrone.
	 */
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

	/**
	 * Vérifie si un utilisateur a déjà reçu une récompense pour une attraction.
	 * Si ce n'est pas le cas, une nouvelle récompense est créée.
	 */
	private UserReward getReward(User user, VisitedLocation visitedLocation, Attraction attraction) {
		boolean alreadyRewarded = user.getUserRewards().stream()
				.anyMatch(r -> r.attraction.attractionName.equals(attraction.attractionName));

		if (!alreadyRewarded) {
			int rewardPoints = getRewardPoints(attraction, user);
			return new UserReward(visitedLocation, attraction, rewardPoints);
		}
		return null; // Retourne null si l'utilisateur a déjà reçu cette récompense
	}

	/**
	 * Vérifie si une localisation visitée est proche d'une attraction en utilisant la distance définie.
	 */
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) <= proximityBuffer;
	}

	/**
	 * Retourne les points de récompense attribués à un utilisateur pour une attraction donnée.
	 */
	public int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

	/**
	 * Retourne l'instance de RewardCentral utilisée par ce service.
	 */
	public RewardCentral getRewardsCentral() {
		return rewardsCentral;
	}

	/**
	 * Vérifie si une attraction est dans la zone de proximité définie.
	 */
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return (getDistance(attraction, location) <= attractionProximityRange);
	}

	/**
	 * Calcule la distance entre deux localisations en utilisant la loi de la sphère.
	 * On utilise ici la **formule du haversine** pour calculer la distance entre deux points géographiques.
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
		double nauticalMiles = 60 * Math.toDegrees(angle); // Conversion en miles nautiques
		return STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles; // Conversion en miles terrestres
	}
}
