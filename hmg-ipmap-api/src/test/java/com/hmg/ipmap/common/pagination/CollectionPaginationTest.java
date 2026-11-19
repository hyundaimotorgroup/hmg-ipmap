package com.hmg.ipmap.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.hmg.ipmap.common.handler.GlobalExceptionHandler;
import com.hmg.ipmap.ipmapping.IpMappingServiceImpl;
import com.hmg.ipmap.location.IpMappingRepository;
import com.hmg.ipmap.location.LocationMapper;
import com.hmg.ipmap.location.LocationRepository;
import com.hmg.ipmap.location.LocationServiceImpl;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

@ExtendWith(MockitoExtension.class)
class CollectionPaginationTest {
    @Mock LocationRepository locations;
    @Mock IpMappingRepository mappings;
    @Mock LocationMapper mapper;
    @InjectMocks LocationServiceImpl locationService;
    @InjectMocks IpMappingServiceImpl mappingService;

    @Test
    void omittedSizeRetainsDefault() {
        assertThat(new PaginationRequest(null, null).sizeOrDefault()).isEqualTo(20);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void beanValidationRejectsInvalidPageSize(int size) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new PaginationRequest(size, 0)))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("size");
        }
    }

    @Test
    void beanValidationAcceptsOmittedAndBoundarySizes() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (Integer size : new Integer[] {null, 1, 100}) {
                assertThat(factory.getValidator().validate(new PaginationRequest(size, 0)))
                        .isEmpty();
            }
        }
    }

    @Test
    void invalidSizeProducesHttpBadRequestForBothCollections() throws Exception {
        var mvc =
                standaloneSetup(new CollectionController(locationService, mappingService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        for (String path : new String[] {"/api/v1/locations", "/api/v1/ip-mappings"}) {
            mvc.perform(get(path).param("size", "101")).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(locations, mappings);
    }

    @RestController
    static class CollectionController {
        private final LocationServiceImpl locations;
        private final IpMappingServiceImpl mappings;

        CollectionController(LocationServiceImpl locations, IpMappingServiceImpl mappings) {
            this.locations = locations;
            this.mappings = mappings;
        }

        @GetMapping("/api/v1/locations")
        PaginationResponse<?> locations(@Valid @ModelAttribute PaginationRequest request) {
            return locations.searchWithPagination(request);
        }

        @GetMapping("/api/v1/ip-mappings")
        PaginationResponse<?> mappings(@Valid @ModelAttribute PaginationRequest request) {
            return mappings.searchWithPagination(request);
        }
    }
}
