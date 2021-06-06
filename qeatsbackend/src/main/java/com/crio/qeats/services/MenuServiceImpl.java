
/*
 *
 *  * Copyright (c) Crio.Do 2019. All rights reserved
 *
 */

package com.crio.qeats.services;

import com.crio.qeats.dto.Item;
import com.crio.qeats.dto.Menu;
import com.crio.qeats.exceptions.ItemNotFoundInRestaurantMenuException;
import com.crio.qeats.exchanges.GetMenuResponse;
import com.crio.qeats.repositoryservices.MenuRepositoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MenuServiceImpl implements MenuService {

  @Autowired
  MenuRepositoryService menuRepositoryService;

  @Override
  public GetMenuResponse findMenu(String restaurantId) {
    GetMenuResponse getMenuResponse = new GetMenuResponse();
    Menu menu = menuRepositoryService.findMenu(restaurantId);
    getMenuResponse.setMenu(menu);
    return getMenuResponse;
  }

  @Override
  public Item findItem(String itemId, String restaurantId)
      throws ItemNotFoundInRestaurantMenuException {
    Menu menu = menuRepositoryService.findMenu(restaurantId);

    for (Item item : menu.getItems()) {
      if (itemId.equals(item.getItemId())) {
        return item;
      }
    }
    throw new ItemNotFoundInRestaurantMenuException("No item found matching the itemId " + itemId);
  }
}
