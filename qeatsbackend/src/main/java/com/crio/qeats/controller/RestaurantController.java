/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.controller;

import com.crio.qeats.dto.Cart;
import com.crio.qeats.dto.Order;
import com.crio.qeats.dto.Restaurant;
import com.crio.qeats.exceptions.CartNotFoundException;
import com.crio.qeats.exceptions.EmptyCartException;
import com.crio.qeats.exceptions.ItemNotFromSameRestaurantException;
import com.crio.qeats.exchanges.AddCartRequest;
import com.crio.qeats.exchanges.CartModifiedResponse;
import com.crio.qeats.exchanges.ClearCartRequest;
import com.crio.qeats.exchanges.DeleteCartRequest;
import com.crio.qeats.exchanges.GetCartRequest;
import com.crio.qeats.exchanges.GetMenuResponse;
import com.crio.qeats.exchanges.GetOrdersResponse;
import com.crio.qeats.exchanges.GetRestaurantsRequest;
import com.crio.qeats.exchanges.GetRestaurantsResponse;
import com.crio.qeats.exchanges.PostOrderRequest;
import com.crio.qeats.exchanges.UpdateOrderStatusRequest;
import com.crio.qeats.services.CartAndOrderService;
import com.crio.qeats.services.MenuService;
import com.crio.qeats.services.RestaurantService;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// TODO: CRIO_TASK_MODULE_RESTAURANTSAPI
// Implement Controller using Spring annotations.
// Remember, annotations have various "targets". They can be class level, method level or others.

@RestController
@Log4j2
@RequestMapping(RestaurantController.RESTAURANT_API_ENDPOINT)
public class RestaurantController {

  public static final String RESTAURANT_API_ENDPOINT = "/qeats/v1";
  public static final String RESTAURANTS_API = "/restaurants";
  public static final String MENU_API = "/menu";
  public static final String CART_API = "/cart";
  public static final String CART_ITEM_API = "/cart/item";
  public static final String CART_CLEAR_API = "/cart/clear";
  public static final String POST_ORDER_API = "/order";
  public static final String GET_ORDERS_API = "/orders";

  @Autowired
  private RestaurantService restaurantService;

  @Autowired
  CartAndOrderService cartAndOrderService;

  @Autowired
  MenuService menuService;

  @GetMapping(RESTAURANTS_API)
  public ResponseEntity<GetRestaurantsResponse> getRestaurants(
      GetRestaurantsRequest getRestaurantsRequest) {
    log.info("getRestaurants called with {}", getRestaurantsRequest);
    GetRestaurantsResponse getRestaurantsResponse;
    String searchFor = getRestaurantsRequest.getSearchFor();
    if (getRestaurantsRequest.getLatitude() != null 
        && getRestaurantsRequest.getLongitude() != null
        && getRestaurantsRequest.getLatitude() >= - 90 && getRestaurantsRequest.getLatitude() <= 90
        && getRestaurantsRequest.getLongitude() >= - 180 
        && getRestaurantsRequest.getLongitude() <= 180) {
      if (!(StringUtils.isEmpty(searchFor))) {
        getRestaurantsResponse =
restaurantService.findRestaurantsBySearchQuery(getRestaurantsRequest,
LocalTime.now());
        log.info("getRestaurants returned {}", getRestaurantsResponse);
      } else {
        getRestaurantsResponse =
        restaurantService.findAllRestaurantsCloseBy(getRestaurantsRequest,
LocalTime.now());
      }
      if (getRestaurantsResponse != null) {
        List<Restaurant> restaurantList =  getRestaurantsResponse.getRestaurants();
        for (Restaurant restaurants : restaurantList) {
          restaurants.setName(restaurants.getName().replace("é",
                "?"));
        }
        getRestaurantsResponse.setRestaurants(restaurantList);
        return ResponseEntity.ok().body(getRestaurantsResponse);
      } else {
        return ResponseEntity.ok().body(new GetRestaurantsResponse(new ArrayList<>()));
      }
    } else {
      return ResponseEntity.badRequest().body(null);
    }
  
  }

  // Implement GET Cart for the given userId.
  // API URI: /qeats/v1/cart?userId=arun
  // Method: GET
  // Query Params: userId
  // Success Output:
  // 1). If userId is present return user's cart
  //     - If user has an active cart, then return it
  //     - otherwise return an empty cart
  //
  // 2). If userId is not present then respond with BadHttpRequest.
  // Eg:
  // curl -X GET "http://localhost:8081/qeats/v1/cart?userId=arun"

  @GetMapping(CART_API)
  public ResponseEntity<Cart> getCart(GetCartRequest getCartRequest) {
    log.info("getRestaurants called with {}", getCartRequest);
    Cart getCartResponse = new Cart();
    String userId = getCartRequest.getUserId();
    if (userId == null || userId.equals("")) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(getCartResponse);
    }
    try {
      getCartResponse =
          cartAndOrderService.findOrCreateCart(getCartRequest.getUserId());
      return ResponseEntity.ok().body(getCartResponse);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(getCartResponse);
    }
  }

  @GetMapping(MENU_API)
  public ResponseEntity<GetMenuResponse> getMenu(
      @RequestParam("restaurantId") String restaurantId) {
    GetMenuResponse getMenuResponse = menuService.findMenu(restaurantId);

    log.info("getMenu returned with {}", getMenuResponse);

    return ResponseEntity.ok().body(getMenuResponse);
  }

  // Implement add item to cart
  // API URI: /qeats/v1/cart/item
  // Method: POST
  // Request Body format:
  //  {
  //    "cartId": "1",
  //    "itemId": "10",
  //    "restaurantId": "11"
  //  }
  // 1). If user has an active cart, add item to the cart.
  // 2). Otherwise create an empty cart and add the given item.
  // 3). If item to be added is not from same restaurant the 'cartResponseType' should be
  //     QEatsException.ITEM_NOT_FOUND_IN_RESTAURANT_MENU.
  // curl -X GET "http://localhost:8081/qeats/v1/cart/item"

  @PostMapping(CART_ITEM_API)
  public ResponseEntity<CartModifiedResponse> addItem(
      @RequestBody @Valid AddCartRequest addCartRequest) {
    CartModifiedResponse cartModifiedResponse = new CartModifiedResponse();
    if (addCartRequest.getRestaurantId() == null || addCartRequest.getRestaurantId().equals("")) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
    }
    try {
      cartModifiedResponse =
          cartAndOrderService.addItemToCart(addCartRequest.getItemId(), addCartRequest.getCartId(),
              addCartRequest.getRestaurantId());
      return ResponseEntity.ok().body(cartModifiedResponse);
    } catch (CartNotFoundException | ItemNotFromSameRestaurantException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cartModifiedResponse);
    }
  }


  // TODO: CRIO_TASK_MODULE_MENUAPI
  // Implement remove item from given cartId
  // API URI: /qeats/v1/cart/item
  // Method: DELETE
  // Request Body format:
  //  {
  //    "cartId": "1",
  //    "itemId": "10",
  //    "restaurantId": "11"
  //  }
  //
  // Success Output:
  // 1). If item is present in user cart, then remove it.
  // 2). Otherwise, do nothing.
  // curl -X GET "http://localhost:8081/qeats/v1/cart/item"

  @DeleteMapping(CART_ITEM_API)
  public ResponseEntity<CartModifiedResponse> deleteItem(
      @RequestBody DeleteCartRequest deleteCartRequest) {
    CartModifiedResponse cartModifiedResponse =
        cartAndOrderService.removeItemFromCart(
            deleteCartRequest.getItemId(), deleteCartRequest.getCartId(),
            deleteCartRequest.getRestaurantId());
    return ResponseEntity.ok().body(cartModifiedResponse);

  }


  // TODO: CRIO_TASK_MODULE_MENUAPI
  // Clear cart for the given cartId.
  // API URI: /qeats/v1/cart/item
  // Method: POST
  // Request Body format:
  //  {
  //    "cartId": "1"
  //  }
  //
  // Success Output:
  // 1). If user has an active cart clear it
  // 2). If cartId is not present then cartResponseType should be 103
  // curl -X GET "http://localhost:8081/qeats/v1/cart/item"
  
  @PutMapping(path = CART_CLEAR_API, consumes = "application/json")
   public ResponseEntity<CartModifiedResponse> clearCart(
       @RequestBody @Valid ClearCartRequest clearCartRequest) {
  
    try {
      CartModifiedResponse cartModifiedResponse
           = cartAndOrderService.clearCart(clearCartRequest.getCartId());
      return ResponseEntity.ok().body(cartModifiedResponse);
    } catch (Exception e) {
      return ResponseEntity.badRequest().build();
    }
  }

  // TODO: CRIO_TASK_MODULE_MENUAPI
  // Place order for the given cartId.
  // API URI: /qeats/v1/order
  // Method: POST
  // Request Body format:
  //  {
  //    "cartId": "1"
  //  }
  //
  // Success Output:
  // 1). Place order for the given cartId and clear the cart.
  // 2). If cart is empty then response should be Bad Http Request.
  // curl -X GET "http://localhost:8081/qeats/v1/order"
  @PostMapping(POST_ORDER_API)
  public ResponseEntity<Order> placeOrder(@RequestBody PostOrderRequest postOrderRequest) {
    String cartId = postOrderRequest.getCartId();
    Order order = new Order();
    try {
      order = cartAndOrderService.postOrder(cartId);
      return ResponseEntity.ok().body(order);
    } catch (CartNotFoundException | EmptyCartException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(order);
    }
  }


  // TODO: CRIO_TASK_MODULE_MENUAPI
  // Implement GET list of orders for the given userId.
  // API URI: /qeats/v1/orders?userId=arun
  // Method: GET
  // Query Params: userId
  // Success Output:
  // 1). Return the list of orders for the given userId
  //     - return an empty list of none exists
  // 2). If userId is not present then return empty list of orders.//
  // curl -X GET "http://localhost:8081/qeats/v1/orders?userId=arun"

  @GetMapping(GET_ORDERS_API)
  public ResponseEntity<GetOrdersResponse> getAllOrders(
      @RequestParam("restaurantId") String restaurantId) {
    if (restaurantId == null || restaurantId.equals("")) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
    } else {
      GetOrdersResponse getOrdersResponse =
          cartAndOrderService.getAllOrders(restaurantId);
      return ResponseEntity.ok().body(getOrdersResponse);
    }

  }

  @PutMapping(GET_ORDERS_API)
  public ResponseEntity<GetOrdersResponse> updateOrderStatus(
      @RequestBody UpdateOrderStatusRequest updateOrderStatusRequest) {
    String restaurantId = updateOrderStatusRequest.getRestaurantId();
    String orderId = updateOrderStatusRequest.getOrderId();
    if (restaurantId == null || restaurantId.equals("")
        || orderId == null || orderId.equals("")) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
    } else {
      GetOrdersResponse getOrdersResponse =
          cartAndOrderService.updateStatusOfOrder(restaurantId, orderId,
              updateOrderStatusRequest.getStatus());
      return ResponseEntity.ok().body(getOrdersResponse);
    }
  }
}

