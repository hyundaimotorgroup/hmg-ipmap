package com.hmg.ipmap.iplocation;

import com.hmg.ipmap.common.exception.GlobalErrorResponse;
import com.hmg.ipmap.common.exception.NotFoundException;
import com.hmg.ipmap.iplocation.validator.IpAddress;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/ip-location")
@RequiredArgsConstructor
public class IpLocationController {

    private final IpLocationService service;

    @Operation(
            summary = "Get location of an IP Address",
            description = "Get location details of an IP Address. ",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Location of the IP address is found"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid IP address format",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GlobalErrorResponse.class)))
            })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> findLocation(@Valid @RequestParam @IpAddress String ip) {
        IpLocationResult result = service.findLocationByIpAddress(ip);
        if (result.notFound()) {
            throw new NotFoundException("IP address not found");
        }
        ResponseEntity.BodyBuilder builder =
                ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON);
        result.scope().ifPresent(s -> builder.header("X-HMGIPMAP-SCOPE", s.name()));
        return builder.body(result.body());
    }
}
