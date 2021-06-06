
/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.services;

import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.exchanges.GetRestaurantsRequest;
import com.crio.qeats.exchanges.GetRestaurantsResponse;
import com.crio.qeats.repositoryservices.RestaurantRepositoryService;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RestaurantServiceImpl implements RestaurantService {

  //8AM - 10AM, 1PM-2PM, 7PM-9PM
  private final int peakHourBreakfastStart = 800;
  private final int peakHourBreakfastEnd = 1000;

  private final int peakHourLunchStart = 1300;
  private final int peakHourLunchEnd = 1400;

  private final int peakHourDinnerStart = 1900;
  private final int peakHourDinnerEnd = 2100;
  private final Double peakHoursServingRadiusInKms = 3.0;
  private final Double normalHoursServingRadiusInKms = 5.0;

  @Autowired
  private RestaurantRepositoryService restaurantRepositoryService;

  // TODO: CRIO_TASK_MODULE_RESTAURANTSAPI - Implement findAllRestaurantsCloseby.
  // Check RestaurantService.java file for the interface contract.
  @Override
  public GetRestaurantsResponse findAllRestaurantsCloseBy(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {

    int timing = currentTime.getHour() * 100 + currentTime.getMinute();
    Double latitude = getRestaurantsRequest.getLatitude();
    Double longitude = getRestaurantsRequest.getLongitude();

    if ((timing >= peakHourBreakfastStart && timing <= peakHourBreakfastEnd) || (
        timing >= peakHourLunchStart && timing <= peakHourLunchEnd) || (
        timing >= peakHourDinnerStart && timing <= peakHourDinnerEnd)) {
      return new GetRestaurantsResponse(
          restaurantRepositoryService.findAllRestaurantsCloseBy(latitude, longitude, currentTime,
              peakHoursServingRadiusInKms));      
    } else {
      return new GetRestaurantsResponse(restaurantRepositoryService
          .findAllRestaurantsCloseBy(latitude, longitude, currentTime,
          normalHoursServingRadiusInKms));
    }   
  }

  // TODO: CRIO_TASK_MODULE_RESTAURANTSEARCH
  // Implement findRestaurantsBySearchQuery. The request object has the search string.
  // We have to combine results from multiple sources:
  // 1. Restaurants by name (exact and inexact)
  // 2. Restaurants by cuisines (also called attributes)
  // 3. Restaurants by food items it serves
  // 4. Restaurants by food item attributes (spicy, sweet, etc)
  // Remember, a restaurant must be present only once in the resulting list.
  // Check RestaurantService.java file for the interface contract.
  @Override
  public GetRestaurantsResponse findRestaurantsBySearchQuery(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime) {
    
    Double servingRadiusInKms = isPeakHour(currentTime) 
        ? peakHoursServingRadiusInKms : normalHoursServingRadiusInKms;
    String searchFor = getRestaurantsRequest.getSearchFor();
    List<List<Restaurant>> listOfRestaurantLists = new ArrayList<>();
    if (!searchFor.isEmpty()) {
      listOfRestaurantLists.add(restaurantRepositoryService
          .findRestaurantsByName(getRestaurantsRequest.getLatitude(),
          getRestaurantsRequest.getLongitude(), searchFor, currentTime,
          servingRadiusInKms));

      listOfRestaurantLists.add(restaurantRepositoryService
          .findRestaurantsByAttributes(getRestaurantsRequest.getLatitude(),
          getRestaurantsRequest.getLongitude(), searchFor,
          currentTime, servingRadiusInKms));
      listOfRestaurantLists
           .add(restaurantRepositoryService.findRestaurantsByItemName(
             getRestaurantsRequest.getLatitude(),
          getRestaurantsRequest.getLongitude(), searchFor,
          currentTime, servingRadiusInKms));
      listOfRestaurantLists.add(restaurantRepositoryService.findRestaurantsByItemAttributes(
            getRestaurantsRequest.getLatitude(),
          getRestaurantsRequest.getLongitude(), searchFor,
          currentTime, servingRadiusInKms));
          
      Set<String> restaurantSet = new HashSet<>();
      List<Restaurant> restaurantList = new ArrayList<>();
      for (List<Restaurant> restoList : listOfRestaurantLists) {
        for (Restaurant restaurant : restoList) {
          if (!restaurantSet.contains(restaurant.getRestaurantId())) {
            restaurantList.add(restaurant);
            restaurantSet.add(restaurant.getRestaurantId());
          }
        }
      }
      return new GetRestaurantsResponse(restaurantList);
    } else {
      return new GetRestaurantsResponse(new ArrayList<>());
    }
  }

  private boolean isTimeWithInRange(LocalTime timeNow,
      LocalTime startTime, LocalTime endTime) {
    return timeNow.isAfter(startTime) && timeNow.isBefore(endTime);
  }

  public boolean isPeakHour(LocalTime timeNow) {
    return isTimeWithInRange(timeNow, LocalTime.of(7,59,59),
LocalTime.of(10, 00, 01))
      || isTimeWithInRange(timeNow, LocalTime.of(12,59,59),
LocalTime.of(14,00,01))
      || isTimeWithInRange(timeNow, LocalTime.of(18,59,59),
LocalTime.of(21,00,01));

  }

  // TODO: CRIO_TASK_MODULE_MULTITHREADING
  // Implement multi-threaded version of RestaurantSearch.
  // Implement variant of findRestaurantsBySearchQuery which is at least 1.5x time faster than
  // findRestaurantsBySearchQuery.
  @Override
  public GetRestaurantsResponse findRestaurantsBySearchQueryMt(
      GetRestaurantsRequest getRestaurantsRequest, LocalTime currentTime)
      throws InterruptedException, ExecutionException {

    int timing = currentTime.getHour() * 100 + currentTime.getMinute();

    Double latitude = getRestaurantsRequest.getLatitude();
    Double longitude = getRestaurantsRequest.getLongitude();
    String searchFor = getRestaurantsRequest.getSearchFor();
    
    if (searchFor.equals("")) {
      return new GetRestaurantsResponse(new ArrayList<>());
    } else if ((timing >= peakHourBreakfastStart && timing <= peakHourBreakfastEnd) || (
            timing >= peakHourLunchStart && timing <= peakHourLunchEnd) || (
            timing >= peakHourDinnerStart && timing <= peakHourDinnerEnd)) {
      CompletableFuture<List<Restaurant>> restaurantList1 = restaurantRepositoryService
              .findRestaurantsByNameAsync(
                  latitude, longitude, searchFor, currentTime, peakHoursServingRadiusInKms);
      CompletableFuture<List<Restaurant>> restaurantList2 = restaurantRepositoryService
              .findRestaurantsByAttributesAsync(
                  latitude, longitude, searchFor, currentTime, peakHoursServingRadiusInKms);
      CompletableFuture<List<Restaurant>> restaurantList3 = restaurantRepositoryService
              .findRestaurantsByItemNameAsync(
                  latitude, longitude, searchFor, currentTime, peakHoursServingRadiusInKms);
      CompletableFuture<List<Restaurant>> restaurantList4 = restaurantRepositoryService
              .findRestaurantsByItemAttributesAsync(
                  latitude, longitude, searchFor, currentTime, peakHoursServingRadiusInKms);
    
      CompletableFuture.allOf(restaurantList1, restaurantList2,
              restaurantList3, restaurantList4).join();
    
      List<Restaurant> restaurantList = restaurantList1.get();
      restaurantList.addAll(restaurantList2.get());
      restaurantList.addAll(restaurantList3.get());
      restaurantList.addAll(restaurantList4.get());
      List<Restaurant> responseList = restaurantList.stream().distinct()
              .collect(Collectors.toList());
    
      return new GetRestaurantsResponse(responseList);
    } else {
      CompletableFuture<List<Restaurant>> restaurantList1 = restaurantRepositoryService
              .findRestaurantsByNameAsync(
                  latitude, longitude, searchFor, currentTime, normalHoursServingRadiusInKms);
      CompletableFuture<List<Restaurant>> restaurantList2 = restaurantRepositoryService
              .findRestaurantsByAttributesAsync(
                  latitude, longitude, searchFor, currentTime, normalHoursServingRadiusInKms);
      CompletableFuture<List<Restaurant>> restaurantList3 = restaurantRepositoryService
              .findRestaurantsByItemNameAsync(
                  latitude, longitude, searchFor, currentTime, normalHoursServingRadiusInKms);
      CompletableFuture<List<Restaurant>> restaurantList4 = restaurantRepositoryService
              .findRestaurantsByItemAttributesAsync(
                  latitude, longitude, searchFor, currentTime, normalHoursServingRadiusInKms);
    
      CompletableFuture.allOf(restaurantList1, restaurantList2,
              restaurantList3, restaurantList4).join();
    
      List<Restaurant> restaurantList = restaurantList1.get();
      restaurantList.addAll(restaurantList2.get());
      restaurantList.addAll(restaurantList3.get());
      restaurantList.addAll(restaurantList4.get());
      List<Restaurant> responseList = restaurantList.stream().distinct()
              .collect(Collectors.toList());
      return new GetRestaurantsResponse(responseList);
    }
  }

  
     
}


