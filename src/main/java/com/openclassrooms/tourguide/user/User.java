package com.openclassrooms.tourguide.user;

import gpsUtil.location.VisitedLocation;
import tripPricer.Provider;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class User {

	// --- Champs d'identification ---
	private final UUID userId;
	private final String userName;
	private String phoneNumber;
	private String emailAddress;
	private Date latestLocationTimestamp;

	// --- Préférences de l'utilisateur ---
	private UserPreferences userPreferences = new UserPreferences();

	// --- Listes internes non thread-safe, mais nous allons les verrouiller ---
	private final List<VisitedLocation> visitedLocations = new ArrayList<>();
	private final List<UserReward> userRewards = new ArrayList<>();
	private List<Provider> tripDeals = new ArrayList<>();

	// ------------------------------------------------------------------------
	// Constructeur
	// ------------------------------------------------------------------------

	public User(UUID userId, String userName, String phoneNumber, String emailAddress) {
		this.userId = userId;
		this.userName = userName;
		this.phoneNumber = phoneNumber;
		this.emailAddress = emailAddress;
	}

	// ------------------------------------------------------------------------
	// Getters / Setters basiques
	// ------------------------------------------------------------------------

	public UUID getUserId() {
		return userId;
	}

	public String getUserName() {
		return userName;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}
	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setEmailAddress(String emailAddress) {
		this.emailAddress = emailAddress;
	}
	public String getEmailAddress() {
		return emailAddress;
	}

	public void setLatestLocationTimestamp(Date latestLocationTimestamp) {
		this.latestLocationTimestamp = latestLocationTimestamp;
	}
	public Date getLatestLocationTimestamp() {
		return latestLocationTimestamp;
	}

	// ------------------------------------------------------------------------
	// VisitedLocations (Thread-safe)
	// ------------------------------------------------------------------------

	/**
	 * Ajoute une position visitée (thread-safe).
	 */
	public synchronized void addToVisitedLocations(VisitedLocation visitedLocation) {
		visitedLocations.add(visitedLocation);
	}

	/**
	 * Retourne la liste des positions visitées (thread-safe).
	 */
	public synchronized List<VisitedLocation> getVisitedLocations() {
		return visitedLocations;
	}

	/**
	 * Efface la liste des positions visitées (thread-safe).
	 */
	public synchronized void clearVisitedLocations() {
		visitedLocations.clear();
	}

	/**
	 * Retourne la dernière position visitée (thread-safe).
	 * Peut renvoyer null si la liste est vide.
	 */
	public synchronized VisitedLocation getLastVisitedLocation() {
		if (visitedLocations.isEmpty()) {
			return null;
		}
		return visitedLocations.get(visitedLocations.size() - 1);
	}

	// ------------------------------------------------------------------------
	// UserRewards (Thread-safe)
	// ------------------------------------------------------------------------

	/**
	 * Ajoute une récompense s'il n'y en a pas déjà pour la même attraction (thread-safe).
	 */
	public synchronized void addUserReward(UserReward userReward) {
		boolean alreadyRewarded = userRewards.stream()
				.anyMatch(r -> r.attraction.attractionName.equals(userReward.attraction.attractionName));
		if (!alreadyRewarded) {
			userRewards.add(userReward);
		}
	}

	/**
	 * Retourne la liste des récompenses (thread-safe).
	 */
	public synchronized List<UserReward> getUserRewards() {
		return userRewards;
	}

	// ------------------------------------------------------------------------
	// UserPreferences
	// ------------------------------------------------------------------------

	public UserPreferences getUserPreferences() {
		return userPreferences;
	}
	public void setUserPreferences(UserPreferences userPreferences) {
		this.userPreferences = userPreferences;
	}

	// ------------------------------------------------------------------------
	// Trip Deals
	// ------------------------------------------------------------------------

	public List<Provider> getTripDeals() {
		return tripDeals;
	}

	public void setTripDeals(List<Provider> tripDeals) {
		this.tripDeals = tripDeals;
	}
}
