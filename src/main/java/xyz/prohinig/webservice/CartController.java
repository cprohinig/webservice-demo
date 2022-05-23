package xyz.prohinig.webservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.prohinig.webservice.database.CartDAO;
import xyz.prohinig.webservice.dto.CartDto;
import xyz.prohinig.webservice.dto.CartsDto;
import xyz.prohinig.webservice.mapper.CartMapper;
import xyz.prohinig.webservice.model.Cart;
import xyz.prohinig.webservice.model.Greeting;

import java.util.ArrayList;
import java.util.List;

@RestController
public class CartController {

    private final CartDAO cartDAO;
    private final CartMapper cartMapper;

    @Autowired
    public CartController(CartDAO cartDAO, CartMapper cartMapper) {
        this.cartDAO = cartDAO;
        this.cartMapper = cartMapper;
    }

    @GetMapping("/carts")
    public CartsDto getAllCarts() {
//        return new Greeting(counter.incrementAndGet(), String.format(template, name));
        List<CartDto> carts = new ArrayList<>();
        carts.add(new CartDto(1, 12, 99.9, true));
        carts.add(new CartDto(2, 1, 3.5, true));
        carts.add(new CartDto(3, 1, 3.5, true));

        return new CartsDto(carts.size(), carts);
    }

    @GetMapping("/carts/{cartId}")
    public CartDto getCart(@PathVariable(value = "cartId") int cartId) {
        Cart cart = cartDAO.getCartByID(cartId);
        if (cart == null) {
            return null;
        }

        return cartMapper.toCartDto(cart);
    }
}