package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

public class TestPerformance {

	/*
	 * A note on performance improvements:
	 *
	 * The number of users generated for the high volume tests can be easily
	 * adjusted via this method:
	 *
	 * InternalTestHelper.setInternalUserNumber(100000);
	 *
	 *
	 * These tests can be modified to suit new solutions, just as long as the
	 * performance metrics at the end of the tests remains consistent.
	 *
	 * These are performance metrics that we are trying to hit:
	 *
	 * highVolumeTrackLocation: 100,000 users within 15 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(15) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 *
	 * highVolumeGetRewards: 100,000 users within 20 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(20) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 */

	//@Disabled
	@Test
	public void highVolumeTrackLocation() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		// Users should be incremented up to 100,000, and test finishes within 15
		// minutes
		InternalTestHelper.setInternalUserNumber(100);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		List<User> allUsers = new ArrayList<>();
		allUsers = tourGuideService.getAllUsers();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		// Collecte des futures
		List<CompletableFuture<VisitedLocation>> futures = allUsers.stream()
				.map(user -> tourGuideService.trackUserLocation(user))
				.collect(Collectors.toList());

		// Attendre que toutes les tâches soient terminées
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		long elapsedSeconds = TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());
		System.out.println("highVolumeTrackLocation: Time Elapsed: " + elapsedSeconds + " seconds.");
		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= elapsedSeconds);
	}

	//@Disabled
	@Test
	public void highVolumeGetRewards() {
		// Création des instances nécessaires pour le test
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		// Définir le nombre d'utilisateurs internes pour le test à 100 000
		InternalTestHelper.setInternalUserNumber(100);
		// Création du service TourGuideService (le tracker démarre automatiquement)
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		// MODIF 1 : Récupérer une copie de la liste des utilisateurs pour éviter les problèmes de modification concurrente
		List<User> allUsers = new ArrayList<>(tourGuideService.getAllUsers());

		// Récupérer une attraction qui sera utilisée pour simuler une visite pour chaque utilisateur
		Attraction attraction = gpsUtil.getAttractions().get(0);

		// Pour chaque utilisateur, ajouter une visite correspondant à l'attraction sélectionnée
		// La liste des visites dans la classe User est déjà de type CopyOnWriteArrayList, ce qui la rend thread-safe.
		allUsers.forEach(u -> u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date())));

		// Démarrer le chronomètre pour mesurer le temps total d'exécution
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		// Exécuter les calculs de récompenses en parallèle pour tous les utilisateurs
		List<CompletableFuture<Void>> rewardFutures = allUsers.stream()
				.map(user -> rewardsService.calculateRewardsAsync(user))
				.collect(Collectors.toList());

		// Attendre que toutes les tâches asynchrones soient terminées
		CompletableFuture.allOf(rewardFutures.toArray(new CompletableFuture[0])).join();

		// Arrêter le chronomètre
		stopWatch.stop();

		// MODIF 2 : Ne pas arrêter le tracker ici, afin de conserver son fonctionnement en arrière-plan
		// tourGuideService.tracker.stopTracking(); // Cette ligne est commentée volontairement.

		long elapsedSeconds = TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());
		System.out.println("highVolumeGetRewards: Time Elapsed: " + elapsedSeconds + " seconds.");

		// Vérifier que chaque utilisateur a bien reçu au moins une récompense
		for (User user : allUsers) {
			assertTrue(user.getUserRewards().size() > 0);
		}

		// Vérifier que le temps total d'exécution est inférieur ou égal à 20 minutes
		assertTrue(TimeUnit.MINUTES.toSeconds(20) >= elapsedSeconds);
	}


}