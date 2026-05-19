package com.mycompany.bookstore.testing;

import com.mycompany.bookstore.controller.BookController;
import com.mycompany.bookstore.dto.BookDTO;

public class BookPopulator {
    
    public static void main(String[] args) {
        BookController controller = new BookController();
        System.out.println("==========================================================");
        System.out.println("🚀 POPULATING 20 REALISTIC BOOKS TO THE BOOKSTORE CATALOG");
        System.out.println("==========================================================");
        
        String[] adjectives = {"Effective", "Mastering", "Beginning", "Advanced", "Clean", "Practical", "Modern", "Complete", "Understanding", "Deep Dive Into"};
        String[] subjects = {"Java", "Python", "Rust", "Go Lang", "TypeScript", "Algorithms", "SQL Databases", "Web Development", "Cloud Architecture", "Machine Learning"};
        String[] formats = {"Guide", "Handbook", "Patterns", "Recipes", "Cookbook", "Fundamentals", "Crash Course", "Best Practices", "Essentials", "Concepts"};
        
        String[] authors = {"Joshua Bloch", "Martin Fowler", "Robert C. Martin", "Donald Knuth", "Linus Torvalds", "Grace Hopper", "Alan Turing", "Ada Lovelace", "Dennis Ritchie", "Bjarne Stroustrup"};
        
        int count = 0;
        for (int i = 1; i <= 20; i++) {
            Long id = 1000L + i; // Generates IDs from 1001 to 1100
            
            // Build pseudo-random combinations for realistic book titles
            String adjective = adjectives[(i * 3) % adjectives.length];
            String subject = subjects[(i * 7) % subjects.length];
            String format = formats[(i * 11) % formats.length];
            String name = adjective + " " + subject + " " + format;
            
            String desc = "Discover the core principles of " + subject + " with this expert-level " + format.toLowerCase() + ". Perfect for professional developers and students alike.";
            double price = Math.round((19.99 + (i * 1.83) % 90.0) * 100.0) / 100.0;
            int qty = 5 + (i * 17) % 245;
            String author = authors[i % authors.length];
            String email = author.toLowerCase().replace(" ", "").replace(".", "") + "@example.com";
            
            BookDTO dto = new BookDTO();
            dto.setBookId(id);
            dto.setName(name);
            dto.setDescription(desc);
            dto.setPricePerQty(price);
            dto.setAvailableQty(qty);
            dto.setAuthorName(author);
            dto.setAuthorEmail(email);
            
            Long savedId = controller.add(dto);
            if (savedId != null) {
                count++;
                if (count % 10 == 0) {
                    System.out.println("Serialized " + count + "/20 books... (Saved: " + savedId + ".ser)");
                }
            }
        }
        System.out.println("==========================================================");
        System.out.println("🎉 SUCCESS! " + count + " books successfully serialized & written to disk.");
        System.out.println("Refresh your browser on http://localhost:8080/ to see them!");
        System.out.println("==========================================================");
    }
}
