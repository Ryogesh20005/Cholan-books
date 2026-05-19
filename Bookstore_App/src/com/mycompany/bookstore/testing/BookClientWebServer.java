package com.mycompany.bookstore.testing;

import com.mycompany.bookstore.controller.BookController;
import com.mycompany.bookstore.dto.BookDTO;
import com.mycompany.bookstore.dto.OrderDTO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BookClientWebServer {

    private static final int PORT = 8080;
    private static final BookController controller = new BookController();

    public static void main(String[] args) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            
            // Route handlers
            server.createContext("/", new StaticPageHandler());
            server.createContext("/api/add", new AddBookHandler());
            server.createContext("/api/get", new GetBookHandler());
            server.createContext("/api/list", new ListBooksHandler());
            server.createContext("/api/buy", new BuyBookHandler());
            server.createContext("/api/admin/login", new AdminLoginHandler());
            server.createContext("/api/admin/orders", new AdminOrdersHandler());
            
            server.setExecutor(null); // default executor
            System.out.println("\n========================================================");
            System.out.println("🚀 BOOKSTORE CORE JAVA WEB SERVER ONLINE!");
            System.out.println("🌐 Access UI at: \u001B[36mhttp://localhost:" + PORT + "/\u001B[0m");
            System.out.println("========================================================\n");
            
            server.start();
        } catch (IOException e) {
            System.err.println("❌ Failed to start server on port " + PORT + ": " + e.getMessage());
            System.err.println("Please make sure port " + PORT + " is not occupied by another app.");
        }
    }

    /**
     * Handler to serve the HTML UI.
     */
    static class StaticPageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            String html = getHtmlContent();
            byte[] responseBytes = html.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            
            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
    }

    /**
     * Handler to add a book (POST request).
     */
    static class AddBookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();

            Map<String, String> params = parseQueryParams(sb.toString());

            try {
                Long bookId = Long.parseLong(params.get("bookId"));
                String name = params.get("name");
                String description = params.get("description");
                Double pricePerQty = Double.parseDouble(params.get("pricePerQty"));
                Integer availableQty = Integer.parseInt(params.get("availableQty"));
                String authorName = params.get("authorName");
                String authorEmail = params.get("authorEmail");

                BookDTO dto = new BookDTO();
                dto.setBookId(bookId);
                dto.setName(name);
                dto.setDescription(description);
                dto.setPricePerQty(pricePerQty);
                dto.setAvailableQty(availableQty);
                dto.setAuthorName(authorName);
                dto.setAuthorEmail(authorEmail);

                Long savedId = controller.add(dto);

                if (savedId != null) {
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Book saved successfully!\",\"bookId\":" + savedId + "}");
                } else {
                    sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to save book in repository.\"}");
                }
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid parameters: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    /**
     * Handler to retrieve details of a single book by ID.
     */
    static class GetBookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQueryParams(query);
            String idStr = params.get("id");

            if (idStr == null || idStr.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Missing 'id' parameter.\"}");
                return;
            }

            try {
                Long bookId = Long.parseLong(idStr);
                BookDTO dto = controller.getBook(bookId);

                if (dto != null) {
                    String json = String.format(
                        "{\"success\":true,\"bookId\":%d,\"name\":\"%s\",\"description\":\"%s\",\"pricePerQty\":%.2f,\"availableQty\":%d,\"authorName\":\"%s\",\"authorEmail\":\"%s\"}",
                        dto.getBookId(),
                        escapeJson(dto.getName()),
                        escapeJson(dto.getDescription()),
                        dto.getPricePerQty(),
                        dto.getAvailableQty(),
                        escapeJson(dto.getAuthorName()),
                        escapeJson(dto.getAuthorEmail())
                    );
                    sendJsonResponse(exchange, 200, json);
                } else {
                    sendJsonResponse(exchange, 404, "{\"success\":false,\"message\":\"Book " + bookId + ".ser not found.\"}");
                }
            } catch (NullPointerException e) {
                sendJsonResponse(exchange, 404, "{\"success\":false,\"message\":\"Serialization file for Book ID " + idStr + " not found.\"}");
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"success\":false,\"message\":\"Error deserializing book: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    /**
     * Handler to list all books that exist in the working directory as .ser files.
     */
    static class ListBooksHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            File dir = new File(".");
            File[] files = dir.listFiles((d, name) -> name.endsWith(".ser") && !name.startsWith("ORD-"));
            List<String> jsonBooks = new ArrayList<>();

            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    try {
                        String idStr = fileName.substring(0, fileName.indexOf('.'));
                        Long bookId = Long.parseLong(idStr);
                        BookDTO dto = controller.getBook(bookId);
                        if (dto != null) {
                            jsonBooks.add(String.format(
                                "{\"bookId\":%d,\"name\":\"%s\",\"description\":\"%s\",\"pricePerQty\":%.2f,\"availableQty\":%d,\"authorName\":\"%s\",\"authorEmail\":\"%s\"}",
                                dto.getBookId(),
                                escapeJson(dto.getName()),
                                escapeJson(dto.getDescription()),
                                dto.getPricePerQty(),
                                dto.getAvailableQty(),
                                escapeJson(dto.getAuthorName()),
                                escapeJson(dto.getAuthorEmail())
                            ));
                        }
                    } catch (Exception e) {
                        // Skip unparseable files
                    }
                }
            }

            String jsonList = "[" + String.join(",", jsonBooks) + "]";
            sendJsonResponse(exchange, 200, jsonList);
        }
    }

    /**
     * Handler to process book purchases (updates availableQty & re-serializes).
     */
    static class BuyBookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();

            Map<String, String> params = parseQueryParams(sb.toString());

            try {
                Long bookId = Long.parseLong(params.get("bookId"));
                Integer purchaseQty = Integer.parseInt(params.get("purchaseQty"));
                String buyerName = params.get("buyerName");
                String buyerEmail = params.get("buyerEmail");
                String buyerAddress = params.get("buyerAddress");

                BookDTO dto = controller.getBook(bookId);
                if (dto == null) {
                    sendJsonResponse(exchange, 404, "{\"success\":false,\"message\":\"Book not found.\"}");
                    return;
                }

                if (dto.getAvailableQty() < purchaseQty) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Insufficient stock! Only " + dto.getAvailableQty() + " copies available.\"}");
                    return;
                }

                // Deduct stock
                dto.setAvailableQty(dto.getAvailableQty() - purchaseQty);
                controller.add(dto); // Re-serialize updated book to disk!

                double totalPrice = dto.getPricePerQty() * purchaseQty;

                // Create and serialize order record persistently!
                OrderDTO order = new OrderDTO();
                order.setOrderId("ORD-" + System.currentTimeMillis());
                order.setBookId(bookId);
                order.setBookName(dto.getName());
                order.setQuantity(purchaseQty);
                order.setTotalPrice(totalPrice);
                order.setBuyerName(buyerName);
                order.setBuyerEmail(buyerEmail);
                order.setBuyerAddress(buyerAddress);
                order.setOrderDate(new java.util.Date());

                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(order.getOrderId() + ".ser"))) {
                    oos.writeObject(order);
                } catch (IOException e) {
                    System.err.println("❌ Failed to serialize order: " + e.getMessage());
                }

                String receiptJson = String.format(
                    "{\"success\":true,\"message\":\"Purchase complete!\",\"bookName\":\"%s\",\"pricePerQty\":%.2f,\"qtyPurchased\":%d,\"totalPrice\":%.2f,\"buyerName\":\"%s\",\"buyerEmail\":\"%s\",\"updatedQty\":%d}",
                    escapeJson(dto.getName()),
                    dto.getPricePerQty(),
                    purchaseQty,
                    totalPrice,
                    escapeJson(buyerName),
                    escapeJson(buyerEmail),
                    dto.getAvailableQty()
                );
                sendJsonResponse(exchange, 200, receiptJson);

            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Transaction failed: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    /**
     * Admin login handler (checks username "Admin" and password "admin123")
     */
    static class AdminLoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();

            Map<String, String> params = parseQueryParams(sb.toString());
            String username = params.get("username");
            String password = params.get("password");

            if ("Admin".equals(username) && "admin123".equals(password)) {
                sendJsonResponse(exchange, 200, "{\"success\":true,\"token\":\"admin-secure-session-token\"}");
            } else {
                sendJsonResponse(exchange, 401, "{\"success\":false,\"message\":\"Invalid username or password. Access Denied.\"}");
            }
        }
    }

    /**
     * Admin orders list handler (scans and deserializes all order files)
     */
    static class AdminOrdersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQueryParams(query);
            String token = params.get("token");

            if (!"admin-secure-session-token".equals(token)) {
                sendJsonResponse(exchange, 401, "{\"success\":false,\"message\":\"Unauthorized! Invalid security token.\"}");
                return;
            }

            File dir = new File(".");
            File[] files = dir.listFiles((d, name) -> name.startsWith("ORD-") && name.endsWith(".ser"));
            List<String> jsonOrders = new ArrayList<>();

            if (files != null) {
                for (File file : files) {
                    try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                        OrderDTO order = (OrderDTO) ois.readObject();
                        if (order != null) {
                            jsonOrders.add(String.format(
                                "{\"orderId\":\"%s\",\"bookId\":%d,\"bookName\":\"%s\",\"quantity\":%d,\"totalPrice\":%.2f,\"buyerName\":\"%s\",\"buyerEmail\":\"%s\",\"buyerAddress\":\"%s\",\"orderDate\":\"%s\"}",
                                order.getOrderId(),
                                order.getBookId(),
                                escapeJson(order.getBookName()),
                                order.getQuantity(),
                                order.getTotalPrice(),
                                escapeJson(order.getBuyerName()),
                                escapeJson(order.getBuyerEmail()),
                                escapeJson(order.getBuyerAddress()),
                                order.getOrderDate() != null ? order.getOrderDate().toString() : ""
                            ));
                        }
                    } catch (Exception e) {
                        // Skip corrupted files
                    }
                }
            }

            // Sort newest first
            jsonOrders.sort((a, b) -> b.compareTo(a));

            String jsonList = "[" + String.join(",", jsonOrders) + "]";
            sendJsonResponse(exchange, 200, jsonList);
        }
    }

    // Helper Utilities
    private static void sendResponse(HttpExchange exchange, int code, String text) throws IOException {
        byte[] responseBytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }

    private static void sendJsonResponse(HttpExchange exchange, int code, String json) throws IOException {
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return result;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
                String value = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") : "";
                result.put(key, value);
            } catch (UnsupportedEncodingException e) {
                // Ignore
            }
        }
        return result;
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    /**
     * 100% Simplified, Gorgeous, Premium Storefront for Buyers
     */
    private static String getHtmlContent() {
        return "<!DOCTYPE html>\n" +
               "<html lang=\"en\">\n" +
               "<head>\n" +
               "    <meta charset=\"UTF-8\">\n" +
               "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
               "    <meta name=\"description\" content=\"Discover and buy elite computer science and programming books.\">\n" +
               "    <title>Cholan Books - Premium Storefront</title>\n" +
               "    <link href=\"https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700&display=swap\" rel=\"stylesheet\">\n" +
               "    <style>\n" +
               "        :root {\n" +
               "            --bg: #09090b;\n" +
               "            --glass-bg: rgba(255, 255, 255, 0.03);\n" +
               "            --glass-border: rgba(255, 255, 255, 0.08);\n" +
               "            --primary: #8b5cf6;\n" +
               "            --primary-glow: rgba(139, 92, 246, 0.3);\n" +
               "            --accent: #d946ef;\n" +
               "            --success: #10b981;\n" +
               "            --danger: #ef4444;\n" +
               "            --text: #f4f4f5;\n" +
               "            --text-muted: #a1a1aa;\n" +
               "        }\n" +
               "\n" +
               "        * {\n" +
               "            box-sizing: border-box;\n" +
               "            margin: 0;\n" +
               "            padding: 0;\n" +
               "            font-family: 'Outfit', sans-serif;\n" +
               "        }\n" +
               "\n" +
               "        body {\n" +
               "            background: radial-gradient(circle at 50% 0%, #1e1b4b 0%, var(--bg) 70%);\n" +
               "            color: var(--text);\n" +
               "            min-height: 100vh;\n" +
               "            overflow-x: hidden;\n" +
               "            padding-bottom: 50px;\n" +
               "        }\n" +
               "\n" +
               "        .header {\n" +
               "            display: flex;\n" +
               "            justify-content: space-between;\n" +
               "            align-items: center;\n" +
               "            padding: 20px 40px;\n" +
               "            background: rgba(0, 0, 0, 0.3);\n" +
               "            border-bottom: 1px solid var(--glass-border);\n" +
               "            backdrop-filter: blur(12px);\n" +
               "            position: sticky;\n" +
               "            top: 0;\n" +
               "            z-index: 100;\n" +
               "        }\n" +
               "\n" +
               "        .brand {\n" +
               "            display: flex;\n" +
               "            align-items: center;\n" +
               "            gap: 10px;\n" +
               "            text-decoration: none;\n" +
               "            color: #fff;\n" +
               "        }\n" +
               "\n" +
               "        .brand h1 {\n" +
               "            font-size: 1.5rem;\n" +
               "            font-weight: 700;\n" +
               "            background: linear-gradient(135deg, #a78bfa 0%, var(--accent) 100%);\n" +
               "            -webkit-background-clip: text;\n" +
               "            -webkit-text-fill-color: transparent;\n" +
               "            letter-spacing: -0.5px;\n" +
               "        }\n" +
               "\n" +
               "        .search-container {\n" +
               "            flex: 0.6;\n" +
               "            max-width: 500px;\n" +
               "        }\n" +
               "\n" +
               "        .form-control {\n" +
               "            width: 100%;\n" +
               "            padding: 12px 18px;\n" +
               "            background: rgba(255, 255, 255, 0.04);\n" +
               "            border: 1px solid var(--glass-border);\n" +
               "            border-radius: 12px;\n" +
               "            color: var(--text);\n" +
               "            font-size: 0.95rem;\n" +
               "            outline: none;\n" +
               "            transition: all 0.3s ease;\n" +
               "        }\n" +
               "\n" +
               "        .form-control:focus {\n" +
               "            border-color: var(--primary);\n" +
               "            background: rgba(255, 255, 255, 0.08);\n" +
               "            box-shadow: 0 0 10px var(--primary-glow);\n" +
               "        }\n" +
               "\n" +
               "        .admin-nav-btn {\n" +
               "            padding: 10px 20px;\n" +
               "            font-size: 0.9rem;\n" +
               "            background: rgba(255, 255, 255, 0.06);\n" +
               "            border: 1px solid var(--glass-border);\n" +
               "            color: #fff;\n" +
               "            border-radius: 10px;\n" +
               "            cursor: pointer;\n" +
               "            font-weight: 600;\n" +
               "            transition: all 0.3s;\n" +
               "            display: flex;\n" +
               "            align-items: center;\n" +
               "            gap: 6px;\n" +
               "        }\n" +
               "\n" +
               "        .admin-nav-btn:hover {\n" +
               "            background: var(--accent);\n" +
               "            border-color: var(--accent);\n" +
               "            box-shadow: 0 0 15px rgba(217, 70, 239, 0.4);\n" +
               "        }\n" +
               "\n" +
               "        .hero-banner {\n" +
               "            text-align: center;\n" +
               "            padding: 60px 20px 40px;\n" +
               "            max-width: 800px;\n" +
               "            margin: 0 auto;\n" +
               "        }\n" +
               "\n" +
               "        .hero-banner h2 {\n" +
               "            font-size: 2.5rem;\n" +
               "            font-weight: 700;\n" +
               "            color: #fff;\n" +
               "            margin-bottom: 12px;\n" +
               "            letter-spacing: -1px;\n" +
               "        }\n" +
               "\n" +
               "        .hero-banner p {\n" +
               "            color: var(--text-muted);\n" +
               "            font-size: 1.15rem;\n" +
               "            line-height: 1.5;\n" +
               "        }\n" +
               "\n" +
               "        .storefront-container {\n" +
               "            max-width: 1200px;\n" +
               "            margin: 0 auto;\n" +
               "            padding: 0 30px;\n" +
               "        }\n" +
               "\n" +
               "        .inventory-grid {\n" +
               "            display: grid;\n" +
               "            grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));\n" +
               "            gap: 30px;\n" +
               "            margin-top: 20px;\n" +
               "        }\n" +
               "\n" +
               "        .book-card {\n" +
               "            background: var(--glass-bg);\n" +
               "            border: 1px solid var(--glass-border);\n" +
               "            border-radius: 20px;\n" +
               "            padding: 24px;\n" +
               "            position: relative;\n" +
               "            overflow: hidden;\n" +
               "            transition: transform 0.3s, border-color 0.3s, box-shadow 0.3s;\n" +
               "            backdrop-filter: blur(10px);\n" +
               "            display: flex;\n" +
               "            flex-direction: column;\n" +
               "            justify-content: space-between;\n" +
               "            min-height: 380px;\n" +
               "        }\n" +
               "\n" +
               "        .book-card:hover {\n" +
               "            transform: translateY(-5px);\n" +
               "            border-color: rgba(139, 92, 246, 0.4);\n" +
               "            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.4);\n" +
               "        }\n" +
               "\n" +
               "        .book-card::before {\n" +
               "            content: '';\n" +
               "            position: absolute;\n" +
               "            top: 0;\n" +
               "            left: 0;\n" +
               "            width: 4px;\n" +
               "            height: 100%;\n" +
               "            background: linear-gradient(to bottom, var(--primary), var(--accent));\n" +
               "        }\n" +
               "\n" +
               "        .book-header {\n" +
               "            display: flex;\n" +
               "            justify-content: space-between;\n" +
               "            align-items: center;\n" +
               "            margin-bottom: 15px;\n" +
               "        }\n" +
               "\n" +
               "        .book-category {\n" +
               "            font-size: 0.75rem;\n" +
               "            padding: 4px 10px;\n" +
               "            background: rgba(139, 92, 246, 0.12);\n" +
               "            color: #c084fc;\n" +
               "            border-radius: 20px;\n" +
               "            font-weight: 600;\n" +
               "            border: 1px solid rgba(139, 92, 246, 0.2);\n" +
               "            letter-spacing: 0.5px;\n" +
               "            text-transform: uppercase;\n" +
               "        }\n" +
               "\n" +
               "        .book-price {\n" +
               "            font-size: 1.3rem;\n" +
               "            font-weight: 700;\n" +
               "            color: var(--accent);\n" +
               "        }\n" +
               "\n" +
               "        .book-title {\n" +
               "            font-size: 1.25rem;\n" +
               "            font-weight: 600;\n" +
               "            margin-bottom: 8px;\n" +
               "            color: #fff;\n" +
               "            line-height: 1.3;\n" +
               "        }\n" +
               "\n" +
               "        .book-desc {\n" +
               "            font-size: 0.9rem;\n" +
               "            color: var(--text-muted);\n" +
               "            margin-bottom: 20px;\n" +
               "            line-height: 1.5;\n" +
               "            flex-grow: 1;\n" +
               "        }\n" +
               "\n" +
               "        .book-meta {\n" +
               "            border-top: 1px solid rgba(255, 255, 255, 0.05);\n" +
               "            padding-top: 15px;\n" +
               "            font-size: 0.85rem;\n" +
               "            display: grid;\n" +
               "            grid-template-columns: 1fr;\n" +
               "            gap: 6px;\n" +
               "            margin-bottom: 20px;\n" +
               "        }\n" +
               "\n" +
               "        .meta-item {\n" +
               "            display: flex;\n" +
               "            justify-content: space-between;\n" +
               "            color: var(--text-muted);\n" +
               "        }\n" +
               "\n" +
               "        .meta-item span:last-child {\n" +
               "            color: var(--text);\n" +
               "            font-weight: 500;\n" +
               "        }\n" +
               "\n" +
               "        .btn {\n" +
               "            width: 100%;\n" +
               "            padding: 12px;\n" +
               "            border: none;\n" +
               "            border-radius: 12px;\n" +
               "            font-size: 0.95rem;\n" +
               "            font-weight: 600;\n" +
               "            color: #fff;\n" +
               "            background: linear-gradient(135deg, var(--primary) 0%, var(--accent) 100%);\n" +
               "            cursor: pointer;\n" +
               "            transition: transform 0.2s, box-shadow 0.2s;\n" +
               "            box-shadow: 0 4px 15px rgba(139, 92, 246, 0.25);\n" +
               "            display: flex;\n" +
               "            justify-content: center;\n" +
               "            align-items: center;\n" +
               "            gap: 8px;\n" +
               "        }\n" +
               "\n" +
               "        .btn:hover {\n" +
               "            transform: translateY(-2px);\n" +
               "            box-shadow: 0 6px 20px rgba(139, 92, 246, 0.45);\n" +
               "        }\n" +
               "\n" +
               "        .form-row {\n" +
               "            display: grid;\n" +
               "            grid-template-columns: 1fr 1fr;\n" +
               "            gap: 15px;\n" +
               "        }\n" +
               "\n" +
               "        .toast {\n" +
               "            position: fixed;\n" +
               "            bottom: 25px;\n" +
               "            right: 25px;\n" +
               "            padding: 16px 24px;\n" +
               "            border-radius: 12px;\n" +
               "            background: #18181b;\n" +
               "            border: 1px solid var(--glass-border);\n" +
               "            box-shadow: 0 10px 30px rgba(0,0,0,0.5);\n" +
               "            transform: translateY(100px);\n" +
               "            opacity: 0;\n" +
               "            transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);\n" +
               "            z-index: 3000;\n" +
               "            font-weight: 500;\n" +
               "        }\n" +
               "\n" +
               "        .toast.success {\n" +
               "            border-color: var(--success);\n" +
               "            color: #6ee7b7;\n" +
               "        }\n" +
               "\n" +
               "        .toast.error {\n" +
               "            border-color: var(--danger);\n" +
               "            color: #fca5a5;\n" +
               "        }\n" +
               "\n" +
               "        .toast.show {\n" +
               "            transform: translateY(0);\n" +
               "            opacity: 1;\n" +
               "        }\n" +
               "\n" +
               "        .empty-state {\n" +
               "            text-align: center;\n" +
               "            padding: 60px 40px;\n" +
               "            color: var(--text-muted);\n" +
               "            font-size: 1.1rem;\n" +
               "            border: 1px dashed var(--glass-border);\n" +
               "            border-radius: 20px;\n" +
               "            grid-column: 1 / -1;\n" +
               "            background: rgba(255,255,255,0.005);\n" +
               "        }\n" +
               "\n" +
               "        .modal-overlay {\n" +
               "            display: none;\n" +
               "            position: fixed;\n" +
               "            top: 0;\n" +
               "            left: 0;\n" +
               "            width: 100%;\n" +
               "            height: 100%;\n" +
               "            background: rgba(9, 9, 11, 0.85);\n" +
               "            backdrop-filter: blur(8px);\n" +
               "            z-index: 2000;\n" +
               "            justify-content: center;\n" +
               "            align-items: center;\n" +
               "            padding: 20px;\n" +
               "        }\n" +
               "\n" +
               "        .card {\n" +
               "            background: #0e0e11;\n" +
               "            border: 1px solid var(--glass-border);\n" +
               "            border-radius: 24px;\n" +
               "            padding: 35px;\n" +
               "            box-shadow: 0 15px 40px rgba(0, 0, 0, 0.6);\n" +
               "            backdrop-filter: blur(20px);\n" +
               "        }\n" +
               "\n" +
               "        /* Admin Console Tabs */\n" +
               "        .admin-tabs-nav {\n" +
               "            display: flex;\n" +
               "            gap: 10px;\n" +
               "            margin-bottom: 25px;\n" +
               "            border-bottom: 1px solid var(--glass-border);\n" +
               "            padding-bottom: 15px;\n" +
               "        }\n" +
               "\n" +
               "        .tab-btn {\n" +
               "            padding: 10px 20px;\n" +
               "            background: transparent;\n" +
               "            border: 1px solid transparent;\n" +
               "            color: var(--text-muted);\n" +
               "            cursor: pointer;\n" +
               "            font-weight: 600;\n" +
               "            border-radius: 8px;\n" +
               "            transition: all 0.3s;\n" +
               "        }\n" +
               "\n" +
               "        .tab-btn.active {\n" +
               "            background: rgba(139, 92, 246, 0.15);\n" +
               "            color: #a78bfa;\n" +
               "            border-color: rgba(139, 92, 246, 0.3);\n" +
               "        }\n" +
               "\n" +
               "        .admin-table {\n" +
               "            width: 100%;\n" +
               "            border-collapse: collapse;\n" +
               "            font-size: 0.9rem;\n" +
               "            background: rgba(0,0,0,0.2);\n" +
               "            border-radius: 12px;\n" +
               "            overflow: hidden;\n" +
               "        }\n" +
               "        .admin-table th, .admin-table td {\n" +
               "            padding: 14px 18px;\n" +
               "            border-bottom: 1px solid var(--glass-border);\n" +
               "            text-align: left;\n" +
               "        }\n" +
               "        .admin-table th {\n" +
               "            background: rgba(139, 92, 246, 0.15);\n" +
               "            color: #c084fc;\n" +
               "            font-weight: 600;\n" +
               "        }\n" +
               "        .admin-table tr:hover {\n" +
               "            background: rgba(255, 255, 255, 0.02);\n" +
               "        }\n" +
               "\n" +
               "        .stat-card {\n" +
               "            background: rgba(255, 255, 255, 0.02);\n" +
               "            border: 1px solid var(--glass-border);\n" +
               "            border-radius: 16px;\n" +
               "            padding: 20px;\n" +
               "            text-align: center;\n" +
               "            backdrop-filter: blur(5px);\n" +
               "        }\n" +
               "\n" +
               "        .stat-val {\n" +
               "            font-size: 1.8rem;\n" +
               "            font-weight: 700;\n" +
               "            color: var(--accent);\n" +
               "            margin-top: 5px;\n" +
               "        }\n" +
               "    </style>\n" +
               "</head>\n" +
               "<body>\n" +
               "    <header class=\"header\">\n" +
               "        <a href=\"/\" class=\"brand\">\n" +
               "            <svg viewBox=\"0 0 24 24\" style=\"width:30px; height:30px; fill:var(--primary);\">\n" +
               "                <path d=\"M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z\"/>\n" +
               "            </svg>\n" +
               "            <h1>Cholan Books</h1>\n" +
               "        </a>\n" +
               "\n" +
               "        <div class=\"search-container\">\n" +
               "            <input type=\"text\" id=\"storeSearch\" class=\"form-control\" placeholder=\"Search by title, author, or keyword...\" oninput=\"filterStorefront()\">\n" +
               "        </div>\n" +
               "\n" +
               "        <button class=\"admin-nav-btn\" onclick=\"openAdminClick()\">🔐 Admin Portal</button>\n" +
               "    </header>\n" +
               "\n" +
               "    <section class=\"hero-banner\">\n" +
               "        <h2>Expand Your Digital Horizons</h2>\n" +
               "        <p>Curated and masterfully written textbooks in Programming, Software Design, Architecture, and Engineering. Instant secure ordering.</p>\n" +
               "    </section>\n" +
               "\n" +
               "    <main class=\"storefront-container\">\n" +
               "        <div id=\"inventory-grid\" class=\"inventory-grid\">\n" +
               "            <!-- Dynamically loaded books catalog -->\n" +
               "            <div class=\"empty-state\">Scanning our persistent serialized library files...</div>\n" +
               "        </div>\n" +
               "    </main>\n" +
               "\n" +
               "    <!-- Checkout Modal -->\n" +
               "    <div id=\"checkout-modal\" class=\"modal-overlay\">\n" +
               "        <div class=\"card\" style=\"width: 100%; max-width: 500px; padding: 30px; border-color: var(--primary);\">\n" +
               "            <div class=\"card-title\" style=\"color: var(--accent); margin-bottom: 10px; font-size:1.4rem; font-weight:600;\">\n" +
               "                <span>🛒 Secure Storefront Checkout</span>\n" +
               "            </div>\n" +
               "            <p style=\"color: var(--text-muted); font-size: 0.95rem; margin-bottom: 20px;\">\n" +
               "                You are purchasing: <strong id=\"modal-book-name\" style=\"color: #fff;\">Book Name</strong>\n" +
               "            </p>\n" +
               "            \n" +
               "            <form id=\"checkout-form\" onsubmit=\"submitPurchase(event)\">\n" +
               "                <input type=\"hidden\" id=\"modal-book-id\">\n" +
               "                <input type=\"hidden\" id=\"modal-raw-price\">\n" +
               "                \n" +
               "                <div class=\"form-group\">\n" +
               "                    <label>Price per copy</label>\n" +
               "                    <input type=\"text\" id=\"modal-book-price\" class=\"form-control\" readonly style=\"background: rgba(255,255,255,0.01); color: var(--accent); font-weight: bold;\">\n" +
               "                </div>\n" +
               "\n" +
               "                <div class=\"form-row\">\n" +
               "                    <div class=\"form-group\">\n" +
               "                        <label for=\"purchaseQty\">Quantity to Buy</label>\n" +
               "                        <input type=\"number\" id=\"purchaseQty\" class=\"form-control\" required min=\"1\" value=\"1\" oninput=\"calculateTotal()\">\n" +
               "                    </div>\n" +
               "                    <div class=\"form-group\">\n" +
               "                        <label>Total Price ($)</label>\n" +
               "                        <input type=\"text\" id=\"modal-total-price\" class=\"form-control\" readonly style=\"background: rgba(255,255,255,0.01); color: var(--success); font-weight: bold;\">\n" +
               "                    </div>\n" +
               "                </div>\n" +
               "\n" +
               "                <div class=\"form-group\">\n" +
               "                    <label for=\"buyerName\">Your Name</label>\n" +
               "                    <input type=\"text\" id=\"buyerName\" class=\"form-control\" required placeholder=\"John Doe\">\n" +
               "                </div>\n" +
               "\n" +
               "                <div class=\"form-group\">\n" +
               "                    <label for=\"buyerEmail\">Email Address</label>\n" +
               "                    <input type=\"email\" id=\"buyerEmail\" class=\"form-control\" required placeholder=\"john@domain.com\">\n" +
               "                </div>\n" +
               "\n" +
               "                <div class=\"form-group\">\n" +
               "                    <label for=\"buyerAddress\">Shipping Address</label>\n" +
               "                    <textarea id=\"buyerAddress\" class=\"form-control\" required placeholder=\"123 Main St, City, Country\" style=\"height: 70px; resize: none;\"></textarea>\n" +
               "                </div>\n" +
               "\n" +
               "                <div style=\"display: flex; gap: 15px; margin-top: 25px;\">\n" +
               "                    <button type=\"button\" onclick=\"closeCheckout()\" class=\"btn\" style=\"background: rgba(255,255,255,0.05); border: 1px solid var(--glass-border); color: #fff; box-shadow: none;\">Cancel</button>\n" +
               "                    <button type=\"submit\" class=\"btn\">Place Order</button>\n" +
               "                </div>\n" +
               "            </form>\n" +
               "        </div>\n" +
               "    </div>\n" +
               "\n" +
               "    <!-- Receipt Modal -->\n" +
               "    <div id=\"receipt-modal\" class=\"modal-overlay\">\n" +
               "        <div class=\"card\" style=\"width: 100%; max-width: 480px; padding: 40px; border-color: var(--success); text-align: center; background: radial-gradient(circle at top, rgba(16, 185, 129, 0.1), transparent);\">\n" +
               "            <div style=\"width: 70px; height: 70px; background: rgba(16, 185, 129, 0.15); border: 2px solid var(--success); border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 20px;\">\n" +
               "                <svg viewBox=\"0 0 24 24\" style=\"width: 40px; height: 40px; fill: var(--success);\">\n" +
               "                    <path d=\"M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z\"/>\n" +
               "                </svg>\n" +
               "            </div>\n" +
               "            \n" +
               "            <h2 style=\"font-size: 1.8rem; font-weight: 700; color: var(--success); margin-bottom: 5px;\">Order Placed!</h2>\n" +
               "            <p style=\"color: var(--text-muted); font-size: 0.95rem; margin-bottom: 25px;\">Your transaction has been processed successfully.</p>\n" +
               "            \n" +
               "            <div style=\"background: rgba(255,255,255,0.02); border: 1px solid var(--glass-border); border-radius: 12px; padding: 20px; text-align: left; margin-bottom: 30px; font-size: 0.9rem;\">\n" +
               "                <div style=\"display: flex; justify-content: space-between; margin-bottom: 10px;\">\n" +
               "                    <span style=\"color: var(--text-muted);\">Book Purchased:</span>\n" +
               "                    <strong id=\"receipt-book-name\" style=\"color: #fff;\">Book Name</strong>\n" +
               "                </div>\n" +
               "                <div style=\"display: flex; justify-content: space-between; margin-bottom: 10px;\">\n" +
               "                    <span style=\"color: var(--text-muted);\">Quantity:</span>\n" +
               "                    <strong id=\"receipt-qty\" style=\"color: #fff;\">1</strong>\n" +
               "                </div>\n" +
               "                <div style=\"display: flex; justify-content: space-between; margin-bottom: 10px;\">\n" +
               "                    <span style=\"color: var(--text-muted);\">Total Paid:</span>\n" +
               "                    <strong id=\"receipt-total\" style=\"color: var(--success); font-weight: 700;\">$0.00</strong>\n" +
               "                </div>\n" +
               "                <hr style=\"border: 0; border-top: 1px solid rgba(255, 255, 255, 0.05); margin: 15px 0;\">\n" +
               "                <div style=\"display: flex; justify-content: space-between; margin-bottom: 8px;\">\n" +
               "                    <span style=\"color: var(--text-muted);\">Customer:</span>\n" +
               "                    <span id=\"receipt-customer\" style=\"color: #fff; font-weight: 500;\">John Doe</span>\n" +
               "                </div>\n" +
               "                <div style=\"display: flex; justify-content: space-between;\">\n" +
               "                    <span style=\"color: var(--text-muted);\">Email:</span>\n" +
               "                    <span id=\"receipt-email\" style=\"color: #fff; font-weight: 500;\">john@domain.com</span>\n" +
               "                </div>\n" +
               "            </div>\n" +
               "            \n" +
               "            <button onclick=\"closeReceipt()\" class=\"btn\" style=\"background: var(--success); box-shadow: 0 4px 15px rgba(16, 185, 129, 0.3);\">Done</button>\n" +
               "        </div>\n" +
               "    </div>\n" +
               "\n" +
               "    <!-- Admin Login Modal -->\n" +
               "    <div id=\"admin-login-modal\" class=\"modal-overlay\">\n" +
               "        <div class=\"card\" style=\"width: 100%; max-width: 400px; padding: 30px; border-color: var(--accent);\">\n" +
               "            <div class=\"card-title\" style=\"color: var(--accent); margin-bottom: 15px;\">\n" +
               "                <span>🔐 Admin Access Portal</span>\n" +
               "            </div>\n" +
               "            <form id=\"admin-login-form\" onsubmit=\"submitAdminLogin(event)\">\n" +
               "                <div class=\"form-group\">\n" +
               "                    <label for=\"adminUsername\">Username</label>\n" +
               "                    <input type=\"text\" id=\"adminUsername\" class=\"form-control\" required placeholder=\"Username\">\n" +
               "                </div>\n" +
               "                <div class=\"form-group\">\n" +
               "                    <label for=\"adminPassword\">Password</label>\n" +
               "                    <input type=\"password\" id=\"adminPassword\" class=\"form-control\" required placeholder=\"Password\">\n" +
               "                </div>\n" +
               "                <div style=\"display: flex; gap: 15px; margin-top: 25px;\">\n" +
               "                    <button type=\"button\" onclick=\"closeAdminLogin()\" class=\"btn\" style=\"background: rgba(255,255,255,0.05); border: 1px solid var(--glass-border); color: #fff; box-shadow: none;\">Cancel</button>\n" +
               "                    <button type=\"submit\" class=\"btn\">Login</button>\n" +
               "                </div>\n" +
               "            </form>\n" +
               "        </div>\n" +
               "    </div>\n" +
               "\n" +
               "    <!-- Admin Dashboard Modal -->\n" +
               "    <div id=\"admin-dashboard-modal\" class=\"modal-overlay\">\n" +
               "        <div class=\"card\" style=\"width: 100%; max-width: 1100px; padding: 40px; border-color: var(--accent); max-height: 90vh; overflow-y: auto;\">\n" +
               "            <div style=\"display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--glass-border); padding-bottom: 15px; margin-bottom: 25px;\">\n" +
               "                <h2 style=\"font-size: 1.8rem; font-weight: 700; color: #a78bfa; display: flex; align-items: center; gap: 10px;\">\n" +
               "                    📊 Admin Control Console\n" +
               "                </h2>\n" +
               "                <div style=\"display: flex; gap: 12px;\">\n" +
               "                    <button onclick=\"adminLogout()\" class=\"btn\" style=\"width: auto; padding: 8px 16px; font-size: 0.85rem; background: rgba(239, 68, 68, 0.15); border: 1px solid rgba(239, 68, 68, 0.3); color: #fca5a5; box-shadow: none;\">Logout 🚪</button>\n" +
               "                    <button onclick=\"closeAdminDashboard()\" class=\"btn\" style=\"width: auto; padding: 8px 16px; font-size: 0.85rem; background: rgba(255, 255, 255, 0.05); border: 1px solid var(--glass-border); color: #fff; box-shadow: none;\">Close ❌</button>\n" +
               "                </div>\n" +
               "            </div>\n" +
               "\n" +
               "            <!-- Tabs Navigation -->\n" +
               "            <div class=\"admin-tabs-nav\">\n" +
               "                <button onclick=\"switchTab('orders')\" id=\"tab-orders\" class=\"tab-btn active\">📊 Sales & Orders</button>\n" +
               "                <button onclick=\"switchTab('add-book')\" id=\"tab-add-book\" class=\"tab-btn\">📥 Add New Book</button>\n" +
               "            </div>\n" +
               "\n" +
               "            <!-- TAB 1: ORDERS -->\n" +
               "            <div id=\"tab-content-orders\">\n" +
               "                <!-- Stat Summaries -->\n" +
               "                <div style=\"display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; margin-bottom: 30px;\">\n" +
               "                    <div class=\"stat-card\">\n" +
               "                        <div style=\"color: var(--text-muted); font-size: 0.9rem; font-weight: 600;\">Total Sales Orders</div>\n" +
               "                        <div id=\"stat-total-orders\" class=\"stat-val\">0</div>\n" +
               "                    </div>\n" +
               "                    <div class=\"stat-card\">\n" +
               "                        <div style=\"color: var(--text-muted); font-size: 0.9rem; font-weight: 600;\">Total Books Sold</div>\n" +
               "                        <div id=\"stat-total-books\" class=\"stat-val\">0</div>\n" +
               "                    </div>\n" +
               "                    <div class=\"stat-card\">\n" +
               "                        <div style=\"color: var(--text-muted); font-size: 0.9rem; font-weight: 600;\">Gross Sales Revenue</div>\n" +
               "                        <div id=\"stat-gross-revenue\" class=\"stat-val\" style=\"color: var(--success);\">$0.00</div>\n" +
               "                    </div>\n" +
               "                </div>\n" +
               "\n" +
               "                <!-- Orders Table List -->\n" +
               "                <div style=\"overflow-x: auto;\">\n" +
               "                    <table class=\"admin-table\">\n" +
               "                        <thead>\n" +
               "                            <tr>\n" +
               "                                <th>Order ID</th>\n" +
               "                                <th>Book Title</th>\n" +
               "                                <th>Qty</th>\n" +
               "                                <th>Total price</th>\n" +
               "                                <th>Customer Name</th>\n" +
               "                                <th>Customer Email</th>\n" +
               "                                <th>Shipping Address</th>\n" +
               "                                <th>Date Time</th>\n" +
               "                            </tr>\n" +
               "                        </thead>\n" +
               "                        <tbody id=\"orders-table-body\">\n" +
               "                            <tr>\n" +
               "                                <td colspan=\"8\" style=\"text-align: center; color: var(--text-muted); padding: 30px;\">No orders recorded in the serialization registry yet.</td>\n" +
               "                            </tr>\n" +
               "                        </tbody>\n" +
               "                    </table>\n" +
               "                </div>\n" +
               "            </div>\n" +
               "\n" +
               "            <!-- TAB 2: ADD BOOK -->\n" +
               "            <div id=\"tab-content-add-book\" style=\"display: none; max-width: 600px; margin: 0 auto;\">\n" +
               "                <form id=\"add-book-form\" onsubmit=\"addBook(event)\" style=\"background: rgba(255,255,255,0.01); border: 1px solid var(--glass-border); padding: 30px; border-radius: 20px;\">\n" +
               "                    <h3 style=\"color: #a78bfa; font-weight: 600; margin-bottom: 20px; font-size:1.2rem;\">Serialize a New Book to Hard Drive</h3>\n" +
               "                    <div class=\"form-group\">\n" +
               "                        <label for=\"bookId\">Book ID</label>\n" +
               "                        <input type=\"number\" id=\"bookId\" class=\"form-control\" required placeholder=\"e.g., 202\">\n" +
               "                    </div>\n" +
               "                    <div class=\"form-group\">\n" +
               "                        <label for=\"name\">Book Name</label>\n" +
               "                        <input type=\"text\" id=\"name\" class=\"form-control\" required placeholder=\"e.g., Effective Java\">\n" +
               "                    </div>\n" +
               "                    <div class=\"form-group\">\n" +
               "                        <label for=\"description\">Description</label>\n" +
               "                        <input type=\"text\" id=\"description\" class=\"form-control\" required placeholder=\"e.g., A comprehensive guide...\">\n" +
               "                    </div>\n" +
               "                    <div class=\"form-row\">\n" +
               "                        <div class=\"form-group\">\n" +
               "                            <label for=\"pricePerQty\">Price ($)</label>\n" +
               "                            <input type=\"number\" step=\"0.01\" id=\"pricePerQty\" class=\"form-control\" required placeholder=\"e.g., 49.99\">\n" +
               "                        </div>\n" +
               "                        <div class=\"form-group\">\n" +
               "                            <label for=\"availableQty\">Quantity</label>\n" +
               "                            <input type=\"number\" id=\"availableQty\" class=\"form-control\" required placeholder=\"e.g., 100\">\n" +
               "                        </div>\n" +
               "                    </div>\n" +
               "                    <div class=\"form-group\">\n" +
               "                        <label for=\"authorName\">Author Name</label>\n" +
               "                        <input type=\"text\" id=\"authorName\" class=\"form-control\" required placeholder=\"e.g., Joshua Bloch\">\n" +
               "                    </div>\n" +
               "                    <div class=\"form-group\">\n" +
               "                        <label for=\"authorEmail\">Author Email</label>\n" +
               "                        <input type=\"email\" id=\"authorEmail\" class=\"form-control\" required placeholder=\"e.g., author@domain.com\">\n" +
               "                    </div>\n" +
               "                    <button type=\"submit\" class=\"btn\" style=\"margin-top: 10px;\">Serialize & Add to Catalog</button>\n" +
               "                </form>\n" +
               "            </div>\n" +
               "        </div>\n" +
               "    </div>\n" +
               "\n" +
               "    <div id=\"toast\" class=\"toast\">Book saved successfully!</div>\n" +
               "\n" +
               "    <script>\n" +
               "        let loadedBooksData = [];\n" +
               "\n" +
               "        // Initial Load\n" +
               "        document.addEventListener('DOMContentLoaded', loadBooks);\n" +
               "\n" +
               "        function showToast(message, type = 'success') {\n" +
               "            const toast = document.getElementById('toast');\n" +
               "            toast.textContent = message;\n" +
               "            toast.className = `toast ${type} show`;\n" +
               "            setTimeout(() => {\n" +
               "                toast.classList.remove('show');\n" +
               "            }, 3000);\n" +
               "        }\n" +
               "\n" +
               "        function filterStorefront() {\n" +
               "            const query = document.getElementById('storeSearch').value.toLowerCase();\n" +
               "            const cards = document.querySelectorAll('.book-card');\n" +
               "            let visibleCount = 0;\n" +
               "            \n" +
               "            cards.forEach(card => {\n" +
               "                const title = card.querySelector('.book-title').textContent.toLowerCase();\n" +
               "                const desc = card.querySelector('.book-desc').textContent.toLowerCase();\n" +
               "                const author = card.querySelector('.meta-author').textContent.toLowerCase();\n" +
               "                \n" +
               "                if (title.includes(query) || desc.includes(query) || author.includes(query)) {\n" +
               "                    card.style.display = 'flex';\n" +
               "                    visibleCount++;\n" +
               "                } else {\n" +
               "                    card.style.display = 'none';\n" +
               "                }\n" +
               "            });\n" +
               "\n" +
               "            const grid = document.getElementById('inventory-grid');\n" +
               "            const existingEmpty = document.getElementById('search-empty-state');\n" +
               "            if (visibleCount === 0) {\n" +
               "                if (!existingEmpty) {\n" +
               "                    const empty = document.createElement('div');\n" +
               "                    empty.id = 'search-empty-state';\n" +
               "                    empty.className = 'empty-state';\n" +
               "                    empty.textContent = `No matches found for \"${document.getElementById('storeSearch').value}\"`;\n" +
               "                    grid.appendChild(empty);\n" +
               "                }\n" +
               "            } else {\n" +
               "                if (existingEmpty) existingEmpty.remove();\n" +
               "            }\n" +
               "        }\n" +
               "\n" +
               "        function openCheckout(bookId) {\n" +
               "            const book = loadedBooksData.find(b => b.bookId == bookId);\n" +
               "            if (!book) {\n" +
               "                showToast('Book details not found on disk.', 'error');\n" +
               "                return;\n" +
               "            }\n" +
               "\n" +
               "            if (book.availableQty <= 0) {\n" +
               "                showToast('Out of Stock! Cannot purchase.', 'error');\n" +
               "                return;\n" +
               "            }\n" +
               "\n" +
               "            document.getElementById('modal-book-id').value = book.bookId;\n" +
               "            document.getElementById('modal-book-name').textContent = book.name;\n" +
               "            document.getElementById('modal-book-price').value = `$${book.pricePerQty.toFixed(2)}`;\n" +
               "            document.getElementById('modal-raw-price').value = book.pricePerQty;\n" +
               "            \n" +
               "            const qtyInput = document.getElementById('purchaseQty');\n" +
               "            qtyInput.max = book.availableQty;\n" +
               "            qtyInput.value = 1;\n" +
               "            \n" +
               "            calculateTotal();\n" +
               "            document.getElementById('checkout-modal').style.display = 'flex';\n" +
               "        }\n" +
               "\n" +
               "        function calculateTotal() {\n" +
               "            const price = parseFloat(document.getElementById('modal-raw-price').value) || 0;\n" +
               "            const qty = parseInt(document.getElementById('purchaseQty').value) || 1;\n" +
               "            document.getElementById('modal-total-price').value = `$${(price * qty).toFixed(2)}`;\n" +
               "        }\n" +
               "\n" +
               "        function closeCheckout() {\n" +
               "            document.getElementById('checkout-modal').style.display = 'none';\n" +
               "            document.getElementById('checkout-form').reset();\n" +
               "        }\n" +
               "\n" +
               "        function submitPurchase(event) {\n" +
               "            event.preventDefault();\n" +
               "            const bookId = document.getElementById('modal-book-id').value;\n" +
               "            const qty = document.getElementById('purchaseQty').value;\n" +
               "            const name = document.getElementById('buyerName').value;\n" +
               "            const email = document.getElementById('buyerEmail').value;\n" +
               "            const address = document.getElementById('buyerAddress').value;\n" +
               "\n" +
               "            const params = new URLSearchParams();\n" +
               "            params.append('bookId', bookId);\n" +
               "            params.append('purchaseQty', qty);\n" +
               "            params.append('buyerName', name);\n" +
               "            params.append('buyerEmail', email);\n" +
               "            params.append('buyerAddress', address);\n" +
               "\n" +
               "            fetch('/api/buy', {\n" +
               "                method: 'POST',\n" +
               "                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },\n" +
               "                body: params\n" +
               "            })\n" +
               "            .then(res => res.json())\n" +
               "            .then(data => {\n" +
               "                if (data.success) {\n" +
               "                    closeCheckout();\n" +
               "                    \n" +
               "                    // Populate receipt details\n" +
               "                    document.getElementById('receipt-book-name').textContent = data.bookName;\n" +
               "                    document.getElementById('receipt-qty').textContent = data.qtyPurchased;\n" +
               "                    document.getElementById('receipt-total').textContent = `$${data.totalPrice.toFixed(2)}`;\n" +
               "                    document.getElementById('receipt-customer').textContent = data.buyerName;\n" +
               "                    document.getElementById('receipt-email').textContent = data.buyerEmail;\n" +
               "                    \n" +
               "                    document.getElementById('receipt-modal').style.display = 'flex';\n" +
               "                    loadBooks();\n" +
               "                } else {\n" +
               "                    showToast(data.message, 'error');\n" +
               "                }\n" +
               "            })\n" +
               "            .catch(err => {\n" +
               "                showToast('Transaction process failed.', 'error');\n" +
               "            });\n" +
               "        }\n" +
               "\n" +
               "        function closeReceipt() {\n" +
               "            document.getElementById('receipt-modal').style.display = 'none';\n" +
               "        }\n" +
               "\n" +
               "        /* Admin Portal Controls */\n" +
               "        function openAdminClick() {\n" +
               "            const token = sessionStorage.getItem('adminToken');\n" +
               "            if (token === 'admin-secure-session-token') {\n" +
               "                loadAdminDashboard();\n" +
               "            } else {\n" +
               "                document.getElementById('admin-login-modal').style.display = 'flex';\n" +
               "            }\n" +
               "        }\n" +
               "\n" +
               "        function closeAdminLogin() {\n" +
               "            document.getElementById('admin-login-modal').style.display = 'none';\n" +
               "            document.getElementById('admin-login-form').reset();\n" +
               "        }\n" +
               "\n" +
               "        function submitAdminLogin(event) {\n" +
               "            event.preventDefault();\n" +
               "            const user = document.getElementById('adminUsername').value;\n" +
               "            const pass = document.getElementById('adminPassword').value;\n" +
               "\n" +
               "            const params = new URLSearchParams();\n" +
               "            params.append('username', user);\n" +
               "            params.append('password', pass);\n" +
               "\n" +
               "            fetch('/api/admin/login', {\n" +
               "                method: 'POST',\n" +
               "                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },\n" +
               "                body: params\n" +
               "            })\n" +
               "            .then(res => {\n" +
               "                if (!res.ok) {\n" +
               "                    return res.json().then(err => { throw new Error(err.message); });\n" +
               "                }\n" +
               "                return res.json();\n" +
               "            })\n" +
               "            .then(data => {\n" +
               "                if (data.success) {\n" +
               "                    sessionStorage.setItem('adminToken', data.token);\n" +
               "                    closeAdminLogin();\n" +
               "                    loadAdminDashboard();\n" +
               "                    showToast('Admin logged in successfully!', 'success');\n" +
               "                }\n" +
               "            })\n" +
               "            .catch(err => {\n" +
               "                showToast(err.message || 'Incorrect credentials. Try Admin/admin123', 'error');\n" +
               "            });\n" +
               "        }\n" +
               "\n" +
               "        function switchTab(tabId) {\n" +
               "            document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));\n" +
               "            document.getElementById(`tab-${tabId}`).classList.add('active');\n" +
               "            \n" +
               "            if (tabId === 'orders') {\n" +
               "                document.getElementById('tab-content-orders').style.display = 'block';\n" +
               "                document.getElementById('tab-content-add-book').style.display = 'none';\n" +
               "            } else if (tabId === 'add-book') {\n" +
               "                document.getElementById('tab-content-orders').style.display = 'none';\n" +
               "                document.getElementById('tab-content-add-book').style.display = 'block';\n" +
               "            }\n" +
               "        }\n" +
               "\n" +
               "        function loadAdminDashboard() {\n" +
               "            const token = sessionStorage.getItem('adminToken');\n" +
               "            fetch(`/api/admin/orders?token=${token}`)\n" +
               "            .then(res => {\n" +
               "                if (!res.ok) throw new Error('Unauthorized session.');\n" +
               "                return res.json();\n" +
               "            })\n" +
               "            .then(orders => {\n" +
               "                document.getElementById('admin-dashboard-modal').style.display = 'flex';\n" +
               "                switchTab('orders'); // Default tab\n" +
               "                \n" +
               "                const tbody = document.getElementById('orders-table-body');\n" +
               "                tbody.innerHTML = '';\n" +
               "                \n" +
               "                if (orders.length === 0) {\n" +
               "                    tbody.innerHTML = '<tr><td colspan=\"8\" style=\"text-align: center; color: var(--text-muted); padding:30px;\">No orders recorded in the serialization registry yet.</td></tr>';\n" +
               "                    document.getElementById('stat-total-orders').textContent = '0';\n" +
               "                    document.getElementById('stat-total-books').textContent = '0';\n" +
               "                    document.getElementById('stat-gross-revenue').textContent = '$0.00';\n" +
               "                    return;\n" +
               "                }\n" +
               "                \n" +
               "                let totalQty = 0;\n" +
               "                let grossRev = 0;\n" +
               "                \n" +
               "                orders.forEach(ord => {\n" +
               "                    totalQty += ord.quantity;\n" +
               "                    grossRev += ord.totalPrice;\n" +
               "                    \n" +
               "                    const row = document.createElement('tr');\n" +
               "                    row.innerHTML = `\n" +
               "                        <td style=\"font-weight: 600; color: #a78bfa;\">${ord.orderId}</td>\n" +
               "                        <td style=\"font-weight: 500; color: #fff;\">${ord.bookName} <span style=\"font-size:0.75rem; color:var(--text-muted);\">(ID: ${ord.bookId})</span></td>\n" +
               "                        <td>${ord.quantity}</td>\n" +
               "                        <td style=\"color: var(--success); font-weight:700;\">$${ord.totalPrice.toFixed(2)}</td>\n" +
               "                        <td>${ord.buyerName}</td>\n" +
               "                        <td><a href=\"mailto:${ord.buyerEmail}\" style=\"color: #c084fc; text-decoration:none;\">${ord.buyerEmail}</a></td>\n" +
               "                        <td style=\"max-width: 180px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;\" title=\"${ord.buyerAddress}\">${ord.buyerAddress}</td>\n" +
               "                        <td style=\"font-size:0.75rem; color: var(--text-muted);\">${ord.orderDate}</td>\n" +
               "                    `;\n" +
               "                    tbody.appendChild(row);\n" +
               "                });\n" +
               "                \n" +
               "                document.getElementById('stat-total-orders').textContent = orders.length;\n" +
               "                document.getElementById('stat-total-books').textContent = totalQty;\n" +
               "                document.getElementById('stat-gross-revenue').textContent = `$${grossRev.toFixed(2)}`;\n" +
               "            })\n" +
               "            .catch(err => {\n" +
               "                sessionStorage.removeItem('adminToken');\n" +
               "                openAdminClick();\n" +
               "            });\n" +
               "        }\n" +
               "\n" +
               "        function closeAdminDashboard() {\n" +
               "            document.getElementById('admin-dashboard-modal').style.display = 'none';\n" +
               "        }\n" +
               "\n" +
               "        function adminLogout() {\n" +
               "            sessionStorage.removeItem('adminToken');\n" +
               "            closeAdminDashboard();\n" +
               "            showToast('Logged out of Admin Portal.', 'success');\n" +
               "        }\n" +
               "\n" +
               "        function loadBooks() {\n" +
               "            fetch('/api/list')\n" +
               "                .then(res => res.json())\n" +
               "                .then(books => {\n" +
               "                    loadedBooksData = books;\n" +
               "                    books.sort((a, b) => a.bookId - b.bookId);\n" +
               "\n" +
               "                    const grid = document.getElementById('inventory-grid');\n" +
               "                    grid.innerHTML = '';\n" +
               "                    if (books.length === 0) {\n" +
               "                        grid.innerHTML = '<div class=\"empty-state\">No books serialized in directory yet. Please login to admin to add books!</div>';\n" +
               "                        return;\n" +
               "                    }\n" +
               "                    books.forEach((book, i) => {\n" +
               "                        const card = document.createElement('div');\n" +
               "                        card.className = 'book-card';\n" +
               "                        card.style.animationDelay = `${i * 0.02}s`;\n" +
               "                        \n" +
               "                        const isOutOfStock = book.availableQty <= 0;\n" +
               "                        const stockText = isOutOfStock ? '<span style=\"color: var(--danger); font-weight:700;\">SOLD OUT</span>' : `${book.availableQty} left`;\n" +
               "                        const buyBtnStyle = isOutOfStock \n" +
               "                            ? 'background: rgba(239, 68, 68, 0.1); border: 1px solid rgba(239, 68, 68, 0.2); cursor: not-allowed; opacity: 0.5;' \n" +
               "                            : 'background: linear-gradient(135deg, var(--primary) 0%, var(--accent) 100%);';\n" +
               "                        \n" +
               "                        card.innerHTML = `\n" +
               "                            <div>\n" +
               "                                <div class=\"book-header\">\n" +
               "                                    <span class=\"book-category\">Book ID: ${book.bookId}</span>\n" +
               "                                    <span class=\"book-price\">$${book.pricePerQty.toFixed(2)}</span>\n" +
               "                                </div>\n" +
               "                                <h3 class=\"book-title\">${book.name}</h3>\n" +
               "                                <p class=\"book-desc\">${book.description}</p>\n" +
               "                            </div>\n" +
               "                            <div>\n" +
               "                                <div class=\"book-meta\">\n" +
               "                                    <div class=\"meta-item\"><span>Availability:</span> <span>${stockText}</span></div>\n" +
               "                                    <div class=\"meta-item\"><span>Author:</span> <span class=\"meta-author\">${book.authorName}</span></div>\n" +
               "                                </div>\n" +
               "                                <button onclick=\"openCheckout(${book.bookId})\" class=\"btn\" style=\"${buyBtnStyle}\" ${isOutOfStock ? 'disabled' : ''}>\n" +
               "                                    ${isOutOfStock ? 'Out of Stock' : 'Buy Now 🛒'}\n" +
               "                                </button>\n" +
               "                            </div>\n" +
               "                        `;\n" +
               "                        grid.appendChild(card);\n" +
               "                    });\n" +
               "                    filterStorefront(); // Re-apply current search if any\n" +
               "                })\n" +
               "                .catch(err => {\n" +
               "                    console.error('Error fetching catalog:', err);\n" +
               "                });\n" +
               "        }\n" +
               "\n" +
               "        function addBook(event) {\n" +
               "            event.preventDefault();\n" +
               "            const bookId = document.getElementById('bookId').value;\n" +
               "            const name = document.getElementById('name').value;\n" +
               "            const description = document.getElementById('description').value;\n" +
               "            const pricePerQty = document.getElementById('pricePerQty').value;\n" +
               "            const availableQty = document.getElementById('availableQty').value;\n" +
               "            const authorName = document.getElementById('authorName').value;\n" +
               "            const authorEmail = document.getElementById('authorEmail').value;\n" +
               "\n" +
               "            const params = new URLSearchParams();\n" +
               "            params.append('bookId', bookId);\n" +
               "            params.append('name', name);\n" +
               "            params.append('description', description);\n" +
               "            params.append('pricePerQty', pricePerQty);\n" +
               "            params.append('availableQty', availableQty);\n" +
               "            params.append('authorName', authorName);\n" +
               "            params.append('authorEmail', authorEmail);\n" +
               "\n" +
               "            fetch('/api/add', {\n" +
               "                method: 'POST',\n" +
               "                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },\n" +
               "                body: params\n" +
               "            })\n" +
               "            .then(res => res.json())\n" +
               "            .then(data => {\n" +
               "                if (data.success) {\n" +
               "                    showToast(data.message, 'success');\n" +
               "                    document.getElementById('add-book-form').reset();\n" +
               "                    loadBooks();\n" +
               "                    \n" +
               "                    // Refresh orders numbers or dashboard if open\n" +
               "                    const token = sessionStorage.getItem('adminToken');\n" +
               "                    if (token) loadAdminDashboard();\n" +
               "                } else {\n" +
               "                    showToast(data.message, 'error');\n" +
               "                }\n" +
               "            })\n" +
               "            .catch(err => {\n" +
               "                showToast('Failed to connect to the server.', 'error');\n" +
               "            });\n" +
               "        }\n" +
               "    </script>\n" +
               "</body>\n" +
               "</html>";
    }
}
