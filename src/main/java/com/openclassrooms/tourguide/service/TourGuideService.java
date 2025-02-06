package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.NearbyAttractionDTO;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import tripPricer.Provider;
import tripPricer.TripPricer;
import java.util.Comparator;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;
	private final ExecutorService executor = Executors.newFixedThreadPool(16);

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;

		
		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
		Object visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
				: trackUserLocation(user);
		return (VisitedLocation) visitedLocation;
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}
/*
	public void trackAllUsersLocations(List<User> allUsers) {
		List<CompletableFuture<VisitedLocation>> futures = allUsers.stream()
				.map(user -> CompletableFuture.supplyAsync(() -> trackUserLocation(user), executor))
				.collect(Collectors.toList());

		// Attendre la fin de toutes les tâches
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
	}
	*/


	public CompletableFuture<VisitedLocation> trackUserLocation(User user) {
		CompletableFuture<VisitedLocation> visitedLocationCompletableFuture = CompletableFuture.supplyAsync(() -> {
			VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId()); // Simule l'obtention d'une localisation
			return visitedLocation;

		} ,executor).thenApplyAsync((visitedLocation)->{
			user.addToVisitedLocations(visitedLocation );
			return visitedLocation;
		},executor);
		return visitedLocationCompletableFuture;
	}


	public List<NearbyAttractionDTO> getNearByAttractions(VisitedLocation visitedLocation) {
		Location userLocation = visitedLocation.location;

		logger.info("userLocation -------------- : {} {} ",  userLocation.latitude, userLocation.longitude);
		List<Attraction> nearestAttractions = gpsUtil.getAttractions().stream()
				.sorted(Comparator.comparingDouble(attraction -> rewardsService.getDistance(visitedLocation.location, attraction)))
				.limit(5)
				.toList();

		List<NearbyAttractionDTO> nearbyAttractionDTOs = new ArrayList<>();


		for (Attraction attraction : nearestAttractions) {

			NearbyAttractionDTO nearbyAttractionDTO = new NearbyAttractionDTO();
			nearbyAttractionDTO.setAttractionName(attraction.attractionName);
			Location locationAttraction = new Location(attraction.latitude, attraction.longitude);
			nearbyAttractionDTO.setAttractionLocation(locationAttraction);

			Location locationUser = visitedLocation.location;
			nearbyAttractionDTO.setUserLocation(locationUser);
			double distance = rewardsService.getDistance(visitedLocation.location, attraction);
			nearbyAttractionDTO.setDistanceMiles(distance);




			int rewardPoints = rewardsService.getRewardsCentral().getAttractionRewardPoints(attraction.attractionId, visitedLocation.userId);
			nearbyAttractionDTO.setRewardPoints(rewardPoints);


			nearbyAttractionDTOs.add(nearbyAttractionDTO);
		}

		return nearbyAttractionDTOs;
	}

/*
	public List<NearbyAttractionDTO> getNearByAttractions(VisitedLocation visitedLocation) {
		Location userLocation = visitedLocation.location;

		logger.info("userLocation -------------- : {} {} ",  userLocation.latitude, userLocation.longitude);
		List<Attraction> nearestAttractions = gpsUtil.getAttractions().stream()
				.sorted(Comparator.comparingDouble(attraction -> rewardsService.getDistance(visitedLocation.location, attraction)))
				.limit(5)
				.toList();

		List<NearbyAttractionDTO> nearbyAttractionDTOs = new ArrayList<>();


		for (Attraction attraction : nearestAttractions) {

			NearbyAttractionDTO nearbyAttractionDTO = new NearbyAttractionDTO();
			nearbyAttractionDTO.setAttractionName(attraction.attractionName);
			Location locationAttraction = new Location(attraction.latitude, attraction.longitude);
		    nearbyAttractionDTO.setAttractionLocation(locationAttraction);

			Location locationUser = visitedLocation.location;
			nearbyAttractionDTO.setUserLocation(locationUser);
			double distance = rewardsService.getDistance(visitedLocation.location, attraction);
			nearbyAttractionDTO.setDistanceMiles(distance);




			int rewardPoints = rewardsService.getRewardsCentral().getAttractionRewardPoints(attraction.attractionId, visitedLocation.userId);
			nearbyAttractionDTO.setRewardPoints(rewardPoints);


			nearbyAttractionDTOs.add(nearbyAttractionDTO);
		}

		return nearbyAttractionDTOs;
	}
*/


	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
			}
		});
	}

	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
