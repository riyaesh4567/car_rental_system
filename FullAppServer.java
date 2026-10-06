import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.UUID;
import java.util.stream.Collectors;

public class FullAppServer {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/car_rental_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Tata@2025"; // <-- Apna MySQL Password yahan dalein

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // 1. ROOT HANDLER (HTML File Serve)
        server.createContext("/", exchange -> {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                File file = new File("car_index.html");
                if (!file.exists()) {
                    sendJson(exchange, 404, "{\"error\":\"car_index.html not found!\"}");
                    return;
                }
                byte[] bytes = Files.readAllBytes(Paths.get("car_index.html"));
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        // 2. AUTH: LOGIN (/api/auth/login)
        server.createContext("/api/auth/login", exchange -> {
            if (handlePreflight(exchange)) return;
            addCorsHeaders(exchange);

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                String body = getRequestBody(exchange);
                String email = extractJsonVal(body, "email");
                String password = extractJsonVal(body, "password");

                // Validation
                if (email.isEmpty() || password.isEmpty()) {
                    sendJson(exchange, 400, "{\"error\":\"Email and Password are required!\"}");
                    return;
                }

                String sql = "SELECT customer_id, name, email, role FROM customers WHERE email = ? AND password = ?";
                try (Connection conn = getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, email);
                    stmt.setString(2, password);
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        String json = String.format(
                            "{\"userId\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"role\":\"%s\"}",
                            escapeJson(rs.getString("customer_id")),
                            escapeJson(rs.getString("name")),
                            escapeJson(rs.getString("email")),
                            escapeJson(rs.getString("role"))
                        );
                        sendJson(exchange, 200, json);
                    } else {
                        sendJson(exchange, 401, "{\"error\":\"Invalid email or password!\"}");
                    }
                }
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"Server Error: " + escapeJson(e.getMessage()) + "\"}");
            }
        });

        // 3. AUTH: REGISTER (/api/auth/register)
        server.createContext("/api/auth/register", exchange -> {
            if (handlePreflight(exchange)) return;
            addCorsHeaders(exchange);

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                String body = getRequestBody(exchange);
                String name = extractJsonVal(body, "name");
                String email = extractJsonVal(body, "email");
                String password = extractJsonVal(body, "password");

                // Validations
                if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                    sendJson(exchange, 400, "{\"error\":\"All fields are mandatory!\"}");
                    return;
                }
                if (!email.contains("@") || !email.contains(".")) {
                    sendJson(exchange, 400, "{\"error\":\"Invalid email address format!\"}");
                    return;
                }
                if (password.length() < 4) {
                    sendJson(exchange, 400, "{\"error\":\"Password must be at least 4 characters!\"}");
                    return;
                }

                String newCustId = "CUST-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

                try (Connection conn = getConnection()) {
                    // Check duplicate
                    PreparedStatement chk = conn.prepareStatement("SELECT email FROM customers WHERE email = ?");
                    chk.setString(1, email);
                    if (chk.executeQuery().next()) {
                        sendJson(exchange, 409, "{\"error\":\"Email is already registered! Please sign in.\"}");
                        return;
                    }

                    PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO customers (customer_id, name, email, password, role) VALUES (?, ?, ?, ?, 'CUSTOMER')"
                    );
                    ins.setString(1, newCustId);
                    ins.setString(2, name);
                    ins.setString(3, email);
                    ins.setString(4, password);
                    ins.executeUpdate();

                    String json = String.format(
                        "{\"userId\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"role\":\"CUSTOMER\"}",
                        escapeJson(newCustId), escapeJson(name), escapeJson(email)
                    );
                    sendJson(exchange, 200, json);
                }
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"Registration failed: " + escapeJson(e.getMessage()) + "\"}");
            }
        });

        // 4. FLEET: GET ALL CARS (/api/cars)
        server.createContext("/api/cars", exchange -> {
            if (handlePreflight(exchange)) return;
            addCorsHeaders(exchange);

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (Connection conn = getConnection();
                     PreparedStatement stmt = conn.prepareStatement("SELECT * FROM cars");
                     ResultSet rs = stmt.executeQuery()) {

                    StringBuilder json = new StringBuilder("[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) json.append(",");
                        json.append(String.format(
                            "{\"carId\":\"%s\",\"model\":\"%s\",\"pricePerDay\":%.2f,\"isAvailable\":%b,\"image\":\"%s\"}",
                            escapeJson(rs.getString("car_id")),
                            escapeJson(rs.getString("model")),
                            rs.getDouble("price_per_day"),
                            rs.getBoolean("is_available"),
                            escapeJson(rs.getString("image") != null ? rs.getString("image") : "")
                        ));
                        first = false;
                    }
                    json.append("]");
                    sendJson(exchange, 200, json.toString());
                } catch (Exception e) {
                    sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            } else {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            }
        });

        // 5. ADMIN: ADD / UPDATE CAR (/api/cars/add)
        server.createContext("/api/cars/add", exchange -> {
            if (handlePreflight(exchange)) return;
            addCorsHeaders(exchange);

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    String body = getRequestBody(exchange);
                    String carId = extractJsonVal(body, "carId");
                    String model = extractJsonVal(body, "model");
                    String priceStr = extractJsonVal(body, "pricePerDay");
                    String image = extractJsonVal(body, "image");

                    if (carId.isEmpty() || model.isEmpty() || priceStr.isEmpty()) {
                        sendJson(exchange, 400, "{\"error\":\"carId, model, and pricePerDay are required!\"}");
                        return;
                    }

                    double price = Double.parseDouble(priceStr);

                    try (Connection conn = getConnection()) {
                        PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO cars (car_id, model, price_per_day, is_available, image) VALUES (?, ?, ?, true, ?) " +
                            "ON DUPLICATE KEY UPDATE model = VALUES(model), price_per_day = VALUES(price_per_day), image = VALUES(image)"
                        );
                        stmt.setString(1, carId);
                        stmt.setString(2, model);
                        stmt.setDouble(3, price);
                        stmt.setString(4, image.isEmpty() ? "https://images.unsplash.com/photo-1549399542-7e3f8b79c341?w=600" : image);
                        stmt.executeUpdate();

                        sendJson(exchange, 200, "{\"message\":\"Car saved successfully\"}");
                    }
                } catch (Exception e) {
                    sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }
        });

        // 6. ADMIN: DELETE CAR (/api/cars/delete)
        server.createContext("/api/cars/delete", exchange -> {
            if (handlePreflight(exchange)) return;
            addCorsHeaders(exchange);

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    String body = getRequestBody(exchange);
                    String carId = extractJsonVal(body, "carId");

                    if (carId.isEmpty()) {
                        sendJson(exchange, 400, "{\"error\":\"carId is required\"}");
                        return;
                    }

                    try (Connection conn = getConnection();
                         PreparedStatement stmt = conn.prepareStatement("DELETE FROM cars WHERE car_id = ?")) {
                        stmt.setString(1, carId);
                        int affected = stmt.executeUpdate();
                        if (affected > 0) {
                            sendJson(exchange, 200, "{\"message\":\"Vehicle deleted\"}");
                        } else {
                            sendJson(exchange, 404, "{\"error\":\"Vehicle not found\"}");
                        }
                    }
                } catch (Exception e) {
                    sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }
        });

        // 7. RENTALS (/api/rentals)
// 7. RENTALS (/api/rentals)
server.createContext("/api/rentals", exchange -> {
    if (handlePreflight(exchange)) return;
    addCorsHeaders(exchange);
    String method = exchange.getRequestMethod();

    if ("GET".equalsIgnoreCase(method)) {
        String query = exchange.getRequestURI().getQuery();
        String customerIdFilter = "";
        if (query != null && query.contains("customerId=")) {
            customerIdFilter = query.split("customerId=")[1].split("&")[0];
        }

        String sql = "SELECT r.rental_id, r.days, r.total_cost, r.active, r.status, r.age, r.license_no, r.delivery_type, r.delivery_address, " +
                     "c.customer_id, c.name, cr.car_id, cr.model " +
                     "FROM rentals r " +
                     "LEFT JOIN customers c ON r.customer_id = c.customer_id " +
                     "LEFT JOIN cars cr ON r.car_id = cr.car_id " +
                     (customerIdFilter.isEmpty() ? "" : "WHERE r.customer_id = ? ") +
                     "ORDER BY r.rental_id DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (!customerIdFilter.isEmpty()) {
                stmt.setString(1, customerIdFilter);
            }

            ResultSet rs = stmt.executeQuery();
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                json.append(String.format(
                    "{\"rentalId\":%d,\"days\":%d,\"totalCost\":%.2f,\"active\":%b,\"status\":\"%s\",\"age\":%d,\"licenseNo\":\"%s\",\"deliveryType\":\"%s\",\"deliveryAddress\":\"%s\",\"customerId\":\"%s\",\"customerName\":\"%s\",\"carId\":\"%s\",\"model\":\"%s\"}",
                    rs.getInt("rental_id"),
                    rs.getInt("days"),
                    rs.getDouble("total_cost"),
                    rs.getBoolean("active"),
                    escapeJson(rs.getString("status") != null ? rs.getString("status") : "PENDING_APPROVAL"),
                    rs.getInt("age"),
                    escapeJson(rs.getString("license_no") != null ? rs.getString("license_no") : ""),
                    escapeJson(rs.getString("delivery_type") != null ? rs.getString("delivery_type") : "SELF_PICKUP"),
                    escapeJson(rs.getString("delivery_address") != null ? rs.getString("delivery_address") : ""),
                    escapeJson(rs.getString("customer_id") != null ? rs.getString("customer_id") : "N/A"),
                    escapeJson(rs.getString("name") != null ? rs.getString("name") : "Guest"),
                    escapeJson(rs.getString("car_id") != null ? rs.getString("car_id") : "N/A"),
                    escapeJson(rs.getString("model") != null ? rs.getString("model") : "Unknown")
                ));
                first = false;
            }
            json.append("]");
            sendJson(exchange, 200, json.toString());
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }

    } else if ("POST".equalsIgnoreCase(method)) {
        try {
            String body = getRequestBody(exchange);
            String carId = extractJsonVal(body, "carId");
            String customerId = extractJsonVal(body, "customerId");
            int days = Integer.parseInt(extractJsonVal(body, "days"));
            String startDate = extractJsonVal(body, "startDate");
            String endDate = extractJsonVal(body, "endDate");
            int age = Integer.parseInt(extractJsonVal(body, "age"));
            String licenseNo = extractJsonVal(body, "licenseNo");
            String deliveryType = extractJsonVal(body, "deliveryType");
            String address = extractJsonVal(body, "address");

            try (Connection conn = getConnection()) {
                PreparedStatement check = conn.prepareStatement("SELECT price_per_day, is_available FROM cars WHERE car_id = ?");
                check.setString(1, carId);
                ResultSet rs = check.executeQuery();

                if (rs.next() && rs.getBoolean("is_available")) {
                    double total = rs.getDouble("price_per_day") * days;

                    PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO rentals (customer_id, car_id, days, total_cost, active, start_date, end_date, age, license_no, delivery_type, delivery_address, status) " +
                        "VALUES (?, ?, ?, ?, false, ?, ?, ?, ?, ?, ?, 'PENDING_APPROVAL')"
                    );
                    ins.setString(1, customerId);
                    ins.setString(2, carId);
                    ins.setInt(3, days);
                    ins.setDouble(4, total);
                    ins.setString(5, startDate.isEmpty() ? null : startDate);
                    ins.setString(6, endDate.isEmpty() ? null : endDate);
                    ins.setInt(7, age);
                    ins.setString(8, licenseNo);
                    ins.setString(9, deliveryType);
                    ins.setString(10, address);
                    ins.executeUpdate();

                    sendJson(exchange, 200, "{\"message\":\"Request created under review\"}");
                } else {
                    sendJson(exchange, 400, "{\"error\":\"Vehicle is currently unavailable\"}");
                }
            }
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }
});

        // 8. RETURN VEHICLE (/api/return)
// 9. ADMIN APPROVAL ACTION (/api/rentals/action)
server.createContext("/api/rentals/action", exchange -> {
    if (handlePreflight(exchange)) return;
    addCorsHeaders(exchange);

    if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
        try {
            String body = getRequestBody(exchange);
            String rentalId = extractJsonVal(body, "rentalId");
            String action = extractJsonVal(body, "action");

            String newStatus = "APPROVE".equalsIgnoreCase(action) ? "APPROVED" : "REJECTED";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement("UPDATE rentals SET status = ? WHERE rental_id = ?")) {
                stmt.setString(1, newStatus);
                stmt.setInt(2, Integer.parseInt(rentalId));
                stmt.executeUpdate();

                sendJson(exchange, 200, "{\"message\":\"Status updated\"}");
            }
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }
});

// 10. PAYMENT & EMAIL TRIGGER (/api/rentals/pay)
server.createContext("/api/rentals/pay", exchange -> {
    if (handlePreflight(exchange)) return;
    addCorsHeaders(exchange);

    if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
        try {
            String body = getRequestBody(exchange);
            String rentalId = extractJsonVal(body, "rentalId");
            String paymentId = extractJsonVal(body, "paymentId");

            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);

                PreparedStatement q = conn.prepareStatement(
                    "SELECT r.car_id, r.total_cost, r.delivery_type, r.delivery_address, c.email, c.name, cr.model " +
                    "FROM rentals r " +
                    "JOIN customers c ON r.customer_id = c.customer_id " +
                    "JOIN cars cr ON r.car_id = cr.car_id " +
                    "WHERE r.rental_id = ?"
                );
                q.setInt(1, Integer.parseInt(rentalId));
                ResultSet rs = q.executeQuery();

                if (rs.next()) {
                    String carId = rs.getString("car_id");
                    String userEmail = rs.getString("email");
                    String userName = rs.getString("name");
                    String carModel = rs.getString("model");
                    double total = rs.getDouble("total_cost");
                    String handover = rs.getString("delivery_type");
                    String address = rs.getString("delivery_address");

                    PreparedStatement updRent = conn.prepareStatement(
                        "UPDATE rentals SET status = 'PAID_CONFIRMED', active = true, payment_id = ? WHERE rental_id = ?"
                    );
                    updRent.setString(1, paymentId);
                    updRent.setInt(2, Integer.parseInt(rentalId));
                    updRent.executeUpdate();

                    PreparedStatement updCar = conn.prepareStatement("UPDATE cars SET is_available = false WHERE car_id = ?");
                    updCar.setString(1, carId);
                    updCar.executeUpdate();

                    conn.commit();

                    // Console Log Email Dispatch (Examiner-ke demo dekhanor jonno)
                    System.out.println("\n=======================================================");
                    System.out.println(">>> [EMAIL NOTIFICATION SERVICE SENT]");
                    System.out.println("To: " + userEmail + " (" + userName + ")");
                    System.out.println("Subject: Booking Confirmed - " + carModel);
                    System.out.println("Payment Ref: " + paymentId + " | Paid: INR " + total);
                    System.out.println("Handover Mode: " + handover);
                    System.out.println("Location: " + address);
                    System.out.println("Status: Vehicle ready for handover.");
                    System.out.println("=======================================================\n");

                    sendJson(exchange, 200, "{\"message\":\"Payment confirmed\"}");
                } else {
                    conn.rollback();
                    sendJson(exchange, 404, "{\"error\":\"Booking not found\"}");
                }
            }
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }
});

        server.setExecutor(null);
        System.out.println("==================================================");
        System.out.println("SRV FleetOS Server Running: http://localhost:8080");
        System.out.println("==================================================");
        server.start();
    }

    // Database Connection Provider
    private static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {}
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    // Helper: Read Request Body
    private static String getRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "UTF-8"))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    // Helper: Handle CORS Preflight OPTIONS
    private static boolean handlePreflight(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            addCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void sendJson(HttpExchange exchange, int code, String response) throws IOException {
        addCorsHeaders(exchange);
        byte[] bytes = response.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String extractJsonVal(String json, String key) {
        String marker = "\"" + key + "\":";
        int start = json.indexOf(marker);
        if (start == -1) return "";
        start += marker.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        if (end == -1) return "";
        return json.substring(start, end).replace("\"", "").trim();
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}