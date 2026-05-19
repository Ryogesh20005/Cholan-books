package com.mycompany.bookstore.testing;

import com.mycompany.bookstore.controller.BookController;
import com.mycompany.bookstore.dto.BookDTO;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class BookClientInteractive {

    // ANSI Escape codes for stunning terminal colors
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";

    public static void main(String[] args) {
        BookController controller = new BookController();
        Scanner scanner = new Scanner(System.in);

        printHeader();

        boolean running = true;
        while (running) {
            printMenu();
            System.out.print(BOLD + CYAN + "👉 Enter your choice (1-4): " + RESET);
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    addBookInteractive(controller, scanner);
                    break;
                case "2":
                    getBookInteractive(controller, scanner);
                    break;
                case "3":
                    runDefaultTest(controller);
                    break;
                case "4":
                    System.out.println("\n" + BOLD + GREEN + "Thank you for using the Bookstore App! Goodbye! 👋" + RESET + "\n");
                    running = false;
                    break;
                default:
                    System.out.println("\n" + BOLD + RED + "❌ Invalid choice! Please enter a number between 1 and 4." + RESET);
            }
        }
        scanner.close();
    }

    private static void printHeader() {
        System.out.println(BOLD + CYAN + "==========================================================" + RESET);
        System.out.println(BOLD + PURPLE + "          📚  BOOKSTORE CORE JAVA APPLICATION  📚          " + RESET);
        System.out.println(BOLD + CYAN + "==========================================================" + RESET);
        System.out.println("Built with Architecture: Controller ➔ Service ➔ Repository");
        System.out.println("Features: Adapter Design Pattern & Binary Serialization");
        System.out.println(BOLD + CYAN + "==========================================================" + RESET);
    }

    private static void printMenu() {
        System.out.println("\n" + BOLD + YELLOW + "📋 MAIN MENU:" + RESET);
        System.out.println(CYAN + "[1] ➕ Add a New Book (Serialization)" + RESET);
        System.out.println(CYAN + "[2] 🔍 Search / Retrieve Book by ID (Deserialization)" + RESET);
        System.out.println(CYAN + "[3] ⚡ Run Default Hardcoded Test" + RESET);
        System.out.println(RED + "[4] ❌ Exit Application" + RESET);
        System.out.println(BOLD + CYAN + "----------------------------------------------------------" + RESET);
    }

    private static void addBookInteractive(BookController controller, Scanner scanner) {
        System.out.println("\n" + BOLD + GREEN + "--- ➕ ADD A NEW BOOK ---" + RESET);
        
        Long id = null;
        while (id == null) {
            System.out.print("Enter Book ID (e.g., 101, 102): ");
            try {
                id = Long.parseLong(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println(BOLD + RED + "❌ Invalid ID! Please enter a valid number." + RESET);
            }
        }

        System.out.print("Enter Book Name: ");
        String name = scanner.nextLine().trim();

        System.out.print("Enter Book Description: ");
        String desc = scanner.nextLine().trim();

        Double price = null;
        while (price == null) {
            System.out.print("Enter Price per Quantity ($): ");
            try {
                price = Double.parseDouble(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println(BOLD + RED + "❌ Invalid Price! Please enter a valid decimal number." + RESET);
            }
        }

        Integer qty = null;
        while (qty == null) {
            System.out.print("Enter Available Quantity: ");
            try {
                qty = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println(BOLD + RED + "❌ Invalid Quantity! Please enter a valid integer." + RESET);
            }
        }

        System.out.print("Enter Author Name: ");
        String authorName = scanner.nextLine().trim();

        System.out.print("Enter Author Email: ");
        String authorEmail = scanner.nextLine().trim();

        BookDTO bookDTO = new BookDTO();
        bookDTO.setBookId(id);
        bookDTO.setName(name);
        bookDTO.setDescription(desc);
        bookDTO.setPricePerQty(price);
        bookDTO.setAvailableQty(qty);
        bookDTO.setAuthorName(authorName);
        bookDTO.setAuthorEmail(authorEmail);

        System.out.println("\n" + BLUE + "Serializing and saving book to: " + BOLD + id + ".ser" + RESET);
        
        try {
            Long savedId = controller.add(bookDTO);
            if (savedId != null) {
                System.out.println(BOLD + GREEN + "✓ Success! Book successfully serialized and saved with ID: " + savedId + RESET);
            } else {
                System.out.println(BOLD + RED + "❌ Failed to save the book details." + RESET);
            }
        } catch (Exception e) {
            System.out.println(BOLD + RED + "❌ Error occurred: " + e.getMessage() + RESET);
        }
    }

    private static void getBookInteractive(BookController controller, Scanner scanner) {
        System.out.println("\n" + BOLD + GREEN + "--- 🔍 RETRIEVE BOOK BY ID ---" + RESET);
        
        Long id = null;
        while (id == null) {
            System.out.print("Enter Book ID to search (e.g., 111): ");
            try {
                id = Long.parseLong(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println(BOLD + RED + "❌ Invalid ID! Please enter a valid number." + RESET);
            }
        }

        System.out.println(BLUE + "Deserializing and reading book from: " + BOLD + id + ".ser" + RESET);
        
        try {
            BookDTO bookDTO = controller.getBook(id);
            if (bookDTO != null) {
                System.out.println("\n" + BOLD + GREEN + "🎉 Book Found & Deserialized Successfully!" + RESET);
                System.out.println(CYAN + "--------------------------------------------------" + RESET);
                System.out.println(BOLD + "Book ID:         " + RESET + bookDTO.getBookId());
                System.out.println(BOLD + "Book Name:       " + RESET + bookDTO.getName());
                System.out.println(BOLD + "Description:     " + RESET + bookDTO.getDescription());
                System.out.println(BOLD + "Price/Qty:       " + RESET + "$" + bookDTO.getPricePerQty());
                System.out.println(BOLD + "Available Qty:   " + RESET + bookDTO.getAvailableQty());
                System.out.println(BOLD + "Author Name:     " + RESET + bookDTO.getAuthorName());
                System.out.println(BOLD + "Author Email:    " + RESET + bookDTO.getAuthorEmail());
                System.out.println(CYAN + "--------------------------------------------------" + RESET);
            } else {
                System.out.println(BOLD + RED + "❌ No book found with ID: " + id + " (File " + id + ".ser not found or corrupt)" + RESET);
            }
        } catch (NullPointerException e) {
            System.out.println(BOLD + RED + "❌ File " + id + ".ser does not exist! Please create/serialize it first." + RESET);
        } catch (Exception e) {
            System.out.println(BOLD + RED + "❌ Error during deserialization: " + e.getMessage() + RESET);
        }
    }

    private static void runDefaultTest(BookController controller) {
        System.out.println("\n" + BOLD + GREEN + "--- ⚡ RUNNING HARDCODED TEST ---" + RESET);
        BookDTO bookDTO = new BookDTO();
        bookDTO.setBookId(111L);
        bookDTO.setAuthorEmail("author1@gmail.com");
        bookDTO.setAuthorName("Ranjan Sir");
        bookDTO.setAvailableQty(10);
        bookDTO.setName("PlacementPrep");
        bookDTO.setDescription("Book One Description");
        bookDTO.setPricePerQty(88.959);

        System.out.println(BLUE + "1. Serializing Default Book (ID: 111)..." + RESET);
        Long bookId = controller.add(bookDTO);
        if (bookId != null) {
            System.out.println(BOLD + GREEN + "   ✓ Book successfully serialized with ID: " + bookId + RESET);
        }

        System.out.println(BLUE + "2. Deserializing Book (ID: 111)..." + RESET);
        BookDTO retrieved = controller.getBook(111L);
        if (retrieved != null) {
            System.out.println(BOLD + GREEN + "   ✓ Details retrieved: " + RESET);
            System.out.println("     - Name: " + retrieved.getName());
            System.out.println("     - Author: " + retrieved.getAuthorName());
            System.out.println("     - Price: $" + retrieved.getPricePerQty());
        }
    }
}
