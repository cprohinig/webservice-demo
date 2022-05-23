package xyz.prohinig.webservice.database;


import xyz.prohinig.webservice.model.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CartDAO {

    private static final String BURGER_ID_COLUMN = "id";
    private static final String BURGER_PATTY_TYPE_COLUMN = "patty_type";
    private static final String BURGER_CHEESE_COLUMN = "cheese";
    private static final String BURGER_SALAD_COLUMN = "salad";
    private static final String BURGER_TOMATO_COLUMN = "tomato";
    private static final String BURGER_CART_ID_COLUMN = "cart_id";
    private static final String CART_ID_COLUMN = "id";
    private static final String CART_ACTIVE_COLUMN = "active";

    private final DatabaseConnection databaseConnection;

    public CartDAO(DatabaseConnection databaseConnection) {
        this.databaseConnection = databaseConnection;
    }

    public boolean update(Cart cart) {
        try (Connection connection = databaseConnection.getConnection()) {
            if (connection == null) {
                throw new IllegalStateException();
            }

            if (cart.isCheckedOut()) {
                return checkoutCart(cart, connection);
            }

            // if cart has no id, we have to write it to the database
            if (cart.getId() == null) {
                if (!persistCart(cart, connection)) {
                    return false;
                }
            }

            // foreach burger
            for (Burger burger : cart.getBurgers()) {
                // if burger has no id, we have to write it to the database
                if (burger.getId() == null) {
                    if (!persistBurger(burger, cart, connection)) {
                        return false;
                    }
                }
            }

            // delete burgers that have an id but are not part of the cart anymore
            return retainBurgers(cart, connection);
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean checkoutCart(Cart cart, Connection connection) throws SQLException {
        String checkoutCartStatement = "update cart set active = false where id = ?;";

        try (PreparedStatement preparedStatement = connection.prepareStatement(checkoutCartStatement)) {
            preparedStatement.setInt(1, cart.getId());
            preparedStatement.execute();
        }

        return true;
    }

    private boolean retainBurgers(Cart cart, Connection connection) throws SQLException {
        String deleteBurgerStatement;
        if (cart.getBurgers().isEmpty()) {
            deleteBurgerStatement = "delete from burger where cart_id = ?;";
        } else {
            String burgerIdsString = cart.getBurgers()
                    .stream()
                    .map(Burger::getId)
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            deleteBurgerStatement = "delete from burger where cart_id = ? and id not in (" + burgerIdsString + ");";
        }

        try (PreparedStatement preparedStatement = connection.prepareStatement(deleteBurgerStatement)) {
            preparedStatement.setInt(1, cart.getId());
            preparedStatement.executeUpdate();
        }

        return true;
    }

    private boolean persistBurger(Burger burger, Cart cart, Connection connection) throws SQLException {
        String insertBurgerStatement = "insert into burger(patty_type, cheese, salad, tomato, cart_id)"
                + " values(?, ?, ?, ?, ?);";

        try (PreparedStatement preparedStatement =
                     connection.prepareStatement(insertBurgerStatement, PreparedStatement.RETURN_GENERATED_KEYS)) {
            preparedStatement.setString(1, burger.getPattyType().name());
            preparedStatement.setBoolean(2, burger.getCheese() != null);
            preparedStatement.setBoolean(3, burger.getSalad() != null);
            preparedStatement.setBoolean(4, burger.getTomato() != null);
            preparedStatement.setInt(5, cart.getId());

            preparedStatement.execute();
            ResultSet resultSet = preparedStatement.getGeneratedKeys();

            if (resultSet.next()) {
                burger.setId(resultSet.getInt(1));
                return true;
            } else {
                return false;
            }
        }
    }

    private boolean persistCart(Cart cart, Connection connection) throws SQLException {
        String insertCartStatement = "insert into cart default values returning id;";
        try (Statement statement = connection.createStatement()) {
            statement.execute(insertCartStatement, Statement.RETURN_GENERATED_KEYS);
            ResultSet resultSet = statement.getGeneratedKeys();

            if (resultSet.next()) {
                cart.setId(resultSet.getInt(1));
                return true;
            } else {
                return false;
            }
        }
    }

    public Cart getActiveCart() {
        try (Connection connection = databaseConnection.getConnection()) {
            if (connection == null) {
                throw new IllegalStateException();
            }
            String burgersOfActiveCartQuery =
                    "SELECT burger.* FROM burger INNER JOIN cart ON burger.cart_id = cart.id WHERE cart.active = true;";

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(burgersOfActiveCartQuery)) {

                Cart cart = new Cart();

                while (resultSet.next()) {
                    if (cart.getId() == null) {
                        cart.setId(resultSet.getInt(BURGER_CART_ID_COLUMN));
                    }

                    Burger burger = createBurgerFromResultSet(resultSet);
                    cart.addBurger(burger);
                }

                return cart;
            }
        } catch (SQLException e) {
            throw new IllegalStateException();
        }
    }

    public Cart getCartByID(int id) {
        try (Connection connection = databaseConnection.getConnection()) {
            if (connection == null) {
                throw new IllegalStateException();
            }

            Cart cart = getCartByID(connection, id);

            if (cart == null) {
                return null;
            }

            getBurgersForCartAndAdd(connection, cart);

            return cart;
        } catch (SQLException e) {
            throw new IllegalStateException();
        }
    }

    private void getBurgersForCartAndAdd(Connection connection, Cart cart) throws SQLException {
        String burgersOfCartByIDQuery =
                "SELECT burger.id, burger.patty_type, burger.cheese, burger.salad, burger.tomato"
                        + " FROM burger INNER JOIN cart ON burger.cart_id = cart.id WHERE cart.id = ?;";

        try (PreparedStatement preparedStatement = connection.prepareStatement(burgersOfCartByIDQuery)) {
            preparedStatement.setInt(1, cart.getId());
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    Burger burger = createBurgerFromResultSet(resultSet);
                    cart.addBurger(burger);
                }
            }
        }
    }

    public List<Cart> getAllCarts() {
        try (Connection connection = databaseConnection.getConnection()) {
            if (connection == null) {
                throw new IllegalStateException();
            }

            String getAllCartsQuery = "SELECT * from cart;";
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(getAllCartsQuery)) {

                List<Cart> cartList = new ArrayList<>();

                while (resultSet.next()) {
                    Cart cart = new Cart(
                            resultSet.getInt(CART_ID_COLUMN), !resultSet.getBoolean(CART_ACTIVE_COLUMN));

                    getBurgersForCartAndAdd(connection, cart);

                    cartList.add(cart);
                }

                return cartList;
            }
        } catch (SQLException e) {
            throw new IllegalStateException();
        }
    }

    private Cart getCartByID(Connection connection, int id) throws SQLException {
        String getCartByIDQuery = "SELECT * from cart where id = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(getCartByIDQuery)) {
            preparedStatement.setInt(1, id);
            try(ResultSet resultSet = preparedStatement.executeQuery()) {

                if(resultSet.next()) {
                    return new Cart(resultSet.getInt(CART_ID_COLUMN), !resultSet.getBoolean(CART_ACTIVE_COLUMN));
                }
                return null;
            }
        }
    }

    private Burger createBurgerFromResultSet(ResultSet burgerResultSet) throws SQLException {
        int burgerId = burgerResultSet.getInt(BURGER_ID_COLUMN);
        String pattyTypeString = burgerResultSet.getString(BURGER_PATTY_TYPE_COLUMN);
        PattyType pattyType = PattyType.valueOf(pattyTypeString);

        boolean hasCheese = burgerResultSet.getBoolean(BURGER_CHEESE_COLUMN);
        boolean hasTomato = burgerResultSet.getBoolean(BURGER_TOMATO_COLUMN);
        boolean hasSalad = burgerResultSet.getBoolean(BURGER_SALAD_COLUMN);

        return new Burger(
                burgerId,
                pattyType,
                hasCheese ? new Cheese() : null,
                hasSalad ? new Salad() : null,
                hasTomato ? new Tomato() : null
        );
    }

}
