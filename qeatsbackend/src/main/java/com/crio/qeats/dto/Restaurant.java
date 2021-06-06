
/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.validation.constraints.NotNull;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.springframework.data.mongodb.core.mapping.Document;

// Java class that maps to Mongo collection.
@Getter
@Setter
@Data
@Document(collection = "restaurants")
@NoArgsConstructor
@JsonIgnoreProperties({"id"})
public class Restaurant {

  private String id;

  @NotNull
  private String restaurantId;

  @NotNull
  private String name;

  @NotNull
  private String city;

  @NotNull
  private String imageUrl;

  @NotNull
  private Double latitude;

  @NotNull
  private Double longitude;

  @NotNull
  private String opensAt;

  @NotNull
  private String closesAt;

  @NotNull
  private List<String> attributes = new ArrayList<>();

  public void setRestaurant(
      String id,
      String restaurantId,
      String name,
      String city,
      String imageUrl,
      Double latitude,
      Double longitude,
      String opensAt,
      String closesAt,
      List<String> attributes
  ) {
    this.attributes = attributes;
    this.id = id;
    this.restaurantId = restaurantId;
    this.name = name;
    this.city = city;
    this.imageUrl = imageUrl;
    this.latitude = latitude;
    this.longitude = longitude;
    this.opensAt = opensAt;
    this.closesAt = closesAt;


  }

  public String serializeToJson() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    String jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this);
    return jsonString;
  }

}

