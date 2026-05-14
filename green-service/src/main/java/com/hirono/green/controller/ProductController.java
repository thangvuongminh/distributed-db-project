package com.hirono.green.controller;

import com.hirono.green.dto.ProductDto;
import com.hirono.green.entity.Product;
import com.hirono.green.service.ProductService;
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
  public ResponseEntity<Product> getById(@PathVariable String id) {
    return ResponseEntity.ok(productService.getById(id));
  }

  @PostMapping
  public ResponseEntity<Product> create(@RequestBody ProductDto dto) {
    return ResponseEntity.ok(productService.create(dto));
  }

  @PutMapping("/{id}")
  public ResponseEntity<Product> update(@PathVariable String id, @RequestBody ProductDto dto) {
    return ResponseEntity.ok(productService.update(id, dto));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    productService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/info")
  public ResponseEntity<String> info() {
    return ResponseEntity.ok("Green Service (V2 schema) running");
  }
}