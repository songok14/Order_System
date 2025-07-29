package be16.ordersystem.product.controller;

import be16.ordersystem.common.dto.CommonDto;
import be16.ordersystem.product.dto.ProductCreateDto;
import be16.ordersystem.product.dto.ProductSearchDto;
import be16.ordersystem.product.service.ProductService;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/product")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> addProduct(@ModelAttribute ProductCreateDto productCreateDto) {
        Long id = productService.addProduct(productCreateDto);
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result(id)
                        .statusCode(HttpStatus.CREATED.value())
                        .statusMessage("상품등록 완료")
                        .build()
                , HttpStatus.CREATED);
    }

    @GetMapping("/list")
    public ResponseEntity<?> productList(Pageable pageable, ProductSearchDto productSearchDto) {
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result(productService.findAll(pageable, productSearchDto))
                        .statusCode(HttpStatus.OK.value())
                        .statusMessage("상품 목록 조회")
                        .build()
                , HttpStatus.OK);
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<?> productListDetail(@PathVariable Long id) {
        return new ResponseEntity<>(
                CommonDto.builder()
                        .result(productService.productDetail(id))
                        .statusCode(HttpStatus.OK.value())
                        .statusMessage("상품 목록 조회")
                        .build()
                , HttpStatus.OK);
    }
}
