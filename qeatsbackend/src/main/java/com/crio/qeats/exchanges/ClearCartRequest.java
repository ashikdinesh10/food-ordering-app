package com.crio.qeats.exchanges;

import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@NoArgsConstructor
@Getter
@Setter
public class ClearCartRequest {

  @NotNull
private String cartId; 


}
