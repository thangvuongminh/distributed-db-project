package com.hirono.blue.controller;

import com.hirono.blue.dto.ProductDto;
import com.hirono.blue.entity.Product;
import com.hirono.blue.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

  private final ProductService productService;

  @GetMapping
  public ResponseEntity<List<Product>> getAll() {
    return ResponseEntity.ok(productService.getAll());
  }

  @GetMapping("/{id}")
  public ResponseEntity<Product> getById(@PathVariable Long id) {
    return ResponseEntity.ok(productService.getById(id));
  }

  @PostMapping
  public ResponseEntity<Product> create(@RequestBody ProductDto dto) {
    Product created = productService.create(dto);
    return ResponseEntity.ok(created);
  }

  @PutMapping("/{id}")
  public ResponseEntity<Product> update(@PathVariable Long id, @RequestBody ProductDto dto) {
    Product updated = productService.update(id, dto);
    return ResponseEntity.ok(updated);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    productService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/info")
  public ResponseEntity<String> info() {
    return ResponseEntity.ok("Blue Service (V1 schema) running");
  }
}