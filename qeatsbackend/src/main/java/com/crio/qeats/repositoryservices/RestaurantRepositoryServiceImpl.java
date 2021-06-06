/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.repositoryservices;

import ch.hsr.geohash.GeoHash;
import com.crio.qeats.configs.RedisConfiguration;
import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.globals.GlobalConstants;
import com.crio.qeats.models.ItemEntity;
import com.crio.qeats.models.MenuEntity;
import com.crio.qeats.models.RestaurantEntity;
import com.crio.qeats.repositories.ItemRepository;
import com.crio.qeats.repositories.MenuRepository;
import com.crio.qeats.repositories.RestaurantRepository;
import com.crio.qeats.utils.GeoLocation;
import com.crio.qeats.utils.GeoUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Provider;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;

@Service
@Primary
public class RestaurantRepositoryServiceImpl implements RestaurantRepositoryService {

  @Autowired
  private RestaurantRepository restaurantRepository;

  @Autowired
  private ItemRepository itemRepository;

  @Autowired
  private MenuRepository menuRepository;

  @Autowired
  private RedisConfiguration redisConfiguration;

  @Autowired
  private MongoTemplate mongoTemplate;

  @Autowired
  private Provider<ModelMapper> modelMapperProvider;

  private static final int NUM_OF_CHARS = 7;

  private boolean isOpenNow(LocalTime time, RestaurantEntity res) {
    LocalTime openingTime = LocalTime.parse(res.getOpensAt());
    LocalTime closingTime = LocalTime.parse(res.getClosesAt());

    return time.isAfter(openingTime) && time.isBefore(closingTime);
  }

  // TODO: CRIO_TASK_MODULE_NOSQL
  // Objectives:
  // 1. Implement findAllRestaurantsCloseby.
  // 2. Remember to keep the precision of GeoHash in mind while using it as a key.
  // Check RestaurantRepositoryService.java file for the interface contract.
  public List<Restaurant> findAllRestaurantsCloseBy(Double latitude, Double longitude, 
      LocalTime currentTime,
      Double servingRadiusInKms) {

    if (redisConfiguration.isCacheAvailable()) {
      try {
        return findAllRestaurantsCloseFromCache(latitude, longitude, currentTime,
            servingRadiusInKms);
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      return findAllRestaurantsFromDb(latitude, longitude, currentTime, servingRadiusInKms);
    }
    return null;
  }

  public static final double R = 6372.8; // In kilometers

  private static double haversine(double lat1, double lon1, double lat2, double lon2) {
    double differenceLat = Math.toRadians(lat2 - lat1);
    double differenceLon = Math.toRadians(lon2 - lon1);
    lat1 = Math.toRadians(lat1);
    lat2 = Math.toRadians(lat2);

    double a =
        Math.pow(Math.sin(differenceLat / 2), 2) + Math.pow(Math.sin(differenceLon / 2), 2) * Math
            .cos(lat1) * Math.cos(lat2);
    double c = 2 * Math.asin(Math.sqrt(a));
    return R * c;
  }

  /**
   * Implement caching for restaurants closeby.
   */
  private List<Restaurant> findAllRestaurantsCloseFromCache(
      Double latitude, Double longitude,
      LocalTime currentTime, Double servingRadiusInKms) throws IOException {

    List<Restaurant> restaurantList = new ArrayList<Restaurant>();
    Jedis jedis = redisConfiguration.getJedisPool().getResource();

    int timing = currentTime.getHour() * 100 + currentTime.getMinute();
    String geoHash = GeoHash.geoHashStringWithCharacterPrecision(latitude, longitude, 
        NUM_OF_CHARS);

    //If the entry is already available in the cache, then return it from cache to save DB lookup.
    if (jedis.exists(geoHash)) {
      String restaurantsData = jedis.get(geoHash);
      List<String> list = Arrays.asList(restaurantsData.split(";"));
      for (int i = 0; i < list.size(); i++) {
        Restaurant r = new ObjectMapper().readValue(list.get(i), Restaurant.class);
        LocalTime opening = LocalTime.parse(r.getOpensAt());
        LocalTime closing = LocalTime.parse(r.getClosesAt());

        int openingTime = opening.getHour() * 100 + opening.getMinute();
        int closingTime = closing.getHour() * 100 + closing.getMinute();
        if (timing >= openingTime && timing <= closingTime) {
          Double distance = haversine(latitude, longitude, r.getLatitude(),
              r.getLongitude());
          if (distance <= servingRadiusInKms) {
            restaurantList.add(r);
          }
        }
      }
    } else {
      //Whenever the entry is not there in the cache, you will have to populate it from DB.
      restaurantList = findAllRestaurantsFromDb(latitude, longitude,
          currentTime, servingRadiusInKms);
      for (Restaurant r : restaurantList) {

        if (jedis.exists(geoHash)) {
          jedis.append(geoHash, ";" + r.serializeToJson());
          jedis.expire(geoHash, RedisConfiguration.REDIS_ENTRY_EXPIRY_IN_SECONDS);
        } else {
          jedis.set(geoHash, r.serializeToJson());
          jedis.expire(geoHash, RedisConfiguration.REDIS_ENTRY_EXPIRY_IN_SECONDS);
        }
      }
    }
    return restaurantList;
  }

  private List<Restaurant> findAllRestaurantsFromDb(Double latitude,
        Double longitude,
        LocalTime currentTime,
        Double servingRadiusInKms) {

    List<Restaurant> restaurantList = new ArrayList<>();
    ModelMapper mapper = modelMapperProvider.get();
    List<RestaurantEntity> restaurantEntityList = restaurantRepository.findAll();

    for (RestaurantEntity entity : restaurantEntityList) {
      if (isRestaurantCloseByAndOpen(entity, currentTime, latitude, longitude, 
          servingRadiusInKms)) {
        restaurantList.add(mapper.map(entity, Restaurant.class));
      }
    }
    return restaurantList;

  }

  // Find restaurants whose names have an exact or partial match with the search query.
  @Override
  public List<Restaurant> findRestaurantsByName(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {

    ModelMapper modelMapper = modelMapperProvider.get();
    Set<String> restaurantSet = new HashSet<>();
    List<Restaurant> restaurantList = new ArrayList<>();
    Optional<List<RestaurantEntity>> optionalExactRestaurantEntityList
        = restaurantRepository.findRestaurantsByNameExact(searchString);
    if (optionalExactRestaurantEntityList.isPresent()) {
      List<RestaurantEntity> restaurantEntityList =
          optionalExactRestaurantEntityList.get();
      for (RestaurantEntity restaurantEntity : restaurantEntityList) {
        if (isRestaurantCloseByAndOpen(restaurantEntity, currentTime,
            latitude, longitude, servingRadiusInKms)
            && !restaurantSet.contains(restaurantEntity.getRestaurantId())) {
          restaurantList.add(modelMapper.map(restaurantEntity,
              Restaurant.class));
          restaurantSet.add(restaurantEntity.getRestaurantId());
        }
      }
    }   
    Optional<List<RestaurantEntity>> optionalInexactRestaurantEntityList
        = restaurantRepository.findRestaurantsByName(searchString);
    if (optionalInexactRestaurantEntityList.isPresent()) {
      List<RestaurantEntity> restaurantEntityList =
          optionalInexactRestaurantEntityList.get();
      for (RestaurantEntity restaurantEntity : restaurantEntityList) {
        if (isRestaurantCloseByAndOpen(restaurantEntity, currentTime,
            latitude, longitude, servingRadiusInKms)
            && !restaurantSet.contains(restaurantEntity.getRestaurantId())) {
          restaurantList.add(modelMapper.map(restaurantEntity,
              Restaurant.class));
          restaurantSet.add(restaurantEntity.getRestaurantId());
        }
      }
    }
    return restaurantList;
  }

  @Override
  @Async
  public CompletableFuture<List<Restaurant>> findRestaurantsByNameAsync(
      Double latitude, Double longitude, String searchString, LocalTime currentTime,
      Double servingRadiusInKms) {
    BasicQuery query = new BasicQuery("{name: {$regex: /" + searchString + "/i}}");
    ModelMapper modelMapper = modelMapperProvider.get();
    List<RestaurantEntity> restaurants = mongoTemplate
        .find(query, RestaurantEntity.class, "restaurants");
    List<Restaurant> restaurantList = new ArrayList<Restaurant>();

    for (RestaurantEntity restaurant : restaurants) {

      if (isRestaurantCloseByAndOpen(restaurant, currentTime,
          latitude, longitude, servingRadiusInKms)) {
        restaurantList.add(modelMapper.map(restaurant, Restaurant.class));
      }
    }
    return CompletableFuture.completedFuture(restaurantList);
  }

  // Find restaurants whose attributes (cuisines) intersect with the search query.
  @Override
  public List<Restaurant> findRestaurantsByAttributes(
      Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {

    BasicQuery query = new BasicQuery("{attributes: {$regex: /" + searchString + "/i}}");
    List<RestaurantEntity> restaurants = mongoTemplate
        .find(query, RestaurantEntity.class, "restaurants");
    List<Restaurant> restaurantList = new ArrayList<Restaurant>();
    ModelMapper modelMapper = modelMapperProvider.get();
    for (RestaurantEntity restaurant : restaurants) {

      if (isRestaurantCloseByAndOpen(restaurant, currentTime,
          latitude, longitude, servingRadiusInKms)) {
        restaurantList.add(modelMapper.map(restaurant, Restaurant.class));
      }

    }
    return restaurantList;
  }

  @Override
  @Async
  public CompletableFuture<List<Restaurant>> findRestaurantsByAttributesAsync(
      Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    BasicQuery query = new BasicQuery("{attributes: {$regex: /" + searchString + "/i}}");
    ModelMapper modelMapper = modelMapperProvider.get();
    List<RestaurantEntity> restaurants = mongoTemplate
        .find(query, RestaurantEntity.class, "restaurants");
    List<Restaurant> restaurantList = new ArrayList<Restaurant>();
    for (RestaurantEntity restaurant : restaurants) {

      if (isRestaurantCloseByAndOpen(restaurant, currentTime,
          latitude, longitude, servingRadiusInKms)) {
        restaurantList.add(modelMapper.map(restaurant, Restaurant.class));
      }
    }
    return CompletableFuture.completedFuture(restaurantList);
  }

  // Find restaurants which serve food items whose names form a complete or partial match
  // with the search query.
  @Override
  public List<Restaurant> findRestaurantsByItemName(
      Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {

    String regex = String.join("|", Arrays.asList(searchString.split(" ")));
    Optional<List<ItemEntity>> optionalExactItems
        = itemRepository.findItemsByNameExact(searchString);
    Optional<List<ItemEntity>> optionalInexactItems
        = itemRepository.findItemsByNameInexact(regex);
    List<ItemEntity> itemEntityList =
        optionalExactItems.orElseGet(ArrayList:: new);
    List<ItemEntity> inexactItemEntityList =
        optionalInexactItems.orElseGet(ArrayList:: new);
    itemEntityList.addAll(inexactItemEntityList);
    return getRestaurantListServingItems(latitude, longitude,
        currentTime, servingRadiusInKms,
        itemEntityList);
  }

  @Override
  @Async
  public CompletableFuture<List<Restaurant>> findRestaurantsByItemNameAsync(
      Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    BasicQuery query = new BasicQuery("{'items.name': {$regex: /" + searchString + "/i}}");
    ModelMapper modelMapper = modelMapperProvider.get();
    List<MenuEntity> menus = mongoTemplate.find(query, MenuEntity.class, "menus");
    List<RestaurantEntity> restaurants = new ArrayList<>();
    for (MenuEntity menu : menus) {
      String restaurantId = menu.getRestaurantId();
      BasicQuery restaurantQuery = new BasicQuery("{restaurantId:" + restaurantId + "}");
      restaurants.add(mongoTemplate
          .findOne(restaurantQuery, RestaurantEntity.class, "restaurants"));
    }
    List<Restaurant> restaurantList = new ArrayList<Restaurant>();

    for (RestaurantEntity restaurant : restaurants) {

      if (isRestaurantCloseByAndOpen(restaurant, currentTime,
          latitude, longitude, servingRadiusInKms)) {
        restaurantList.add(modelMapper.map(restaurant, Restaurant.class));

      }

    }
    return CompletableFuture.completedFuture(restaurantList);
  }

  // Find restaurants which serve food items whose attributes intersect with the search query.
  @Override
  public List<Restaurant> findRestaurantsByItemAttributes(Double latitude, Double longitude,
      String searchString, LocalTime currentTime, Double servingRadiusInKms) {
    
    List<Pattern> patterns = Arrays
        .stream(searchString.split(" "))
        .map(attr -> Pattern.compile(attr, Pattern.CASE_INSENSITIVE))
        .collect(Collectors.toList());
    Query query = new Query();
    for (Pattern pattern : patterns) {
      query.addCriteria(
          Criteria.where("attributes").regex(pattern));
    }
    List<ItemEntity> itemEntityList = mongoTemplate.find(query,
        ItemEntity.class);
    return getRestaurantListServingItems(latitude, longitude,
        currentTime, servingRadiusInKms,
        itemEntityList);
  }

  @Override
  @Async
  public CompletableFuture<List<Restaurant>> findRestaurantsByItemAttributesAsync(
      Double latitude, Double longitude, String searchString,
      LocalTime currentTime, Double servingRadiusInKms) {
    BasicQuery query = new BasicQuery("{'items.attributes': {$regex: /" + searchString + "/i}}");
    ModelMapper modelMapper = modelMapperProvider.get();
    List<MenuEntity> menus = mongoTemplate.find(query, MenuEntity.class, "menus");
    List<RestaurantEntity> restaurants = new ArrayList<>();
    for (MenuEntity menu : menus) {
      String restaurantId = menu.getRestaurantId();
      BasicQuery restaurantQuery = new BasicQuery("{restaurantId:" + restaurantId + "}");
      restaurants.add(mongoTemplate
          .findOne(restaurantQuery, RestaurantEntity.class, "restaurants"));
    }
    List<Restaurant> restaurantList = new ArrayList<Restaurant>();

    for (RestaurantEntity restaurant : restaurants) {

      if (isRestaurantCloseByAndOpen(restaurant, currentTime,
          latitude, longitude, servingRadiusInKms)) {
        restaurantList.add(modelMapper.map(restaurant, Restaurant.class));

      }

    }
    return CompletableFuture.completedFuture(restaurantList);
  }

  private List<Restaurant> getRestaurantListServingItems(Double
      latitude, Double longitude,
      LocalTime currentTime, Double servingRadiusInKms, List<ItemEntity>
      itemEntityList) {
    List<String> itemIdList = itemEntityList
        .stream()
        .map(ItemEntity::getItemId)
        .collect(Collectors.toList());
    Optional<List<MenuEntity>> optionalMenuEntityList
        = menuRepository.findMenusByItemsItemIdIn(itemIdList);
    Optional<List<RestaurantEntity>> optionalRestaurantEntityList =
        Optional.empty();
    if (optionalMenuEntityList.isPresent()) {
      List<MenuEntity> menuEntityList = optionalMenuEntityList.get();
      List<String> restaurantIdList = menuEntityList
          .stream()
          .map(MenuEntity::getRestaurantId)
          .collect(Collectors.toList());
      optionalRestaurantEntityList = restaurantRepository
.findRestaurantsByRestaurantIdIn(restaurantIdList);
    }
    List<Restaurant> restaurantList = new ArrayList<>();
    ModelMapper modelMapper = modelMapperProvider.get();
    if (optionalRestaurantEntityList.isPresent()) {
      List<RestaurantEntity> restaurantEntityList =
          optionalRestaurantEntityList.get();

      List<RestaurantEntity> restaurantEntitiesFiltered = new ArrayList<>();
      for (RestaurantEntity restaurantEntity : restaurantEntityList) {
        if (isRestaurantCloseByAndOpen(restaurantEntity, currentTime,
            latitude, longitude,
            servingRadiusInKms)) {
          restaurantEntitiesFiltered.add(restaurantEntity);
        }
      }
      restaurantList = restaurantEntitiesFiltered.stream()
.map(restaurantEntity -> modelMapper.map(restaurantEntity,
Restaurant.class))
.collect(Collectors.toList());
    }
    return restaurantList;
  }

  /**
   * Utility method to check if a restaurant is within the serving radius at a given time.
   * @return boolean True if restaurant falls within serving radius and is open, false otherwise
   */
  private boolean isRestaurantCloseByAndOpen(RestaurantEntity restaurantEntity,
      LocalTime currentTime, Double latitude, Double longitude, Double servingRadiusInKms) {
    if (isOpenNow(currentTime, restaurantEntity)) {
      return GeoUtils.findDistanceInKm(latitude, longitude,
          restaurantEntity.getLatitude(), restaurantEntity.getLongitude())
          < servingRadiusInKms;
    }

    return false;
  }



}

