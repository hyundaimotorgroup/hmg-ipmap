package com.hmg.ipmap.ingestion.file;

import com.hmg.ipmap.common.exception.GlobalErrorResponse;
import com.hmg.ipmap.ingestion.file.dto.JobControlRequestDto;
import com.hmg.ipmap.ingestion.file.dto.JobStatusImportTypeResponseDto;
import com.hmg.ipmap.ingestion.file.dto.JobStatusResponseDto;
import com.hmg.ipmap.ingestion.file.dto.UploadResponseDto;
import com.hmg.ipmap.ingestion.file.validator.JobId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/file-imports")
@RequiredArgsConstructor
public class FileImportController {
    private final FileImportService fileImportService;

    @Operation(
            summary = "Upload a file to be imported",
            description = "Upload a file to be imported",
            parameters = {
                @Parameter(
                        name = "job_id",
                        in = ParameterIn.PATH,
                        description = "Job Id",
                        example = "2025-11-05"),
                @Parameter(
                        name = "import_type",
                        in = ParameterIn.PATH,
                        description =
                                "The import type. e.g.: country-zip, city-zip, enterprise-zip, connection-type-zip, anonymous-ip-zip, isp-zip",
                        example = "country-zip"),
                @Parameter(
                        name = "file",
                        content = @Content(mediaType = "multipart/form-data"),
                        schema = @Schema(type = "string", format = "binary"),
                        description = "Upload a file")
            },
            responses = {
                @ApiResponse(
                        responseCode = "202",
                        description = "File is accepted/upload is successful",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(implementation = UploadResponseDto.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Conflict - job already executed",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GlobalErrorResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Bad Request. There is invalid parameters",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GlobalErrorResponse.class)))
            })
    @PutMapping(value = "/{job_id}/{import_type}", consumes = "multipart/form-data")
    public ResponseEntity<UploadResponseDto> upload(
            @RequestParam("file") MultipartFile file,
            @JobId(
                            rejectPastDates = true,
                            message =
                                    "Job ID must be in yyyy-MM-dd format and must not be a past date")
                    @PathVariable(value = "job_id")
                    String jobId,
            @PathVariable(value = "import_type") String importType) {

        UploadResponseDto response = fileImportService.upload(file, jobId, importType);
        return ResponseEntity.accepted().body(response);
    }

    @Operation(
            summary = "Control the job process",
            description =
                    "Once the file has been uploaded, this method is used to start the job and stop the running job",
            parameters = {
                @Parameter(
                        name = "job_id",
                        in = ParameterIn.PATH,
                        description = "Job Id",
                        example = "2025-11-05")
            },
            responses = {
                @ApiResponse(
                        responseCode = "202",
                        description = "Request control is accepted",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                JobStatusResponseDto.class))),
                @ApiResponse(
                        responseCode = "409",
                        description =
                                "Conflict. e.g.: Job not ready to execute, No Batch Cancelled",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GlobalErrorResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Bad Request. e.g: Job already executed, Invalid action, Job Id is invalid",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GlobalErrorResponse.class)))
            })
    @PutMapping(value = "/{job_id}", consumes = "application/json")
    public ResponseEntity<JobStatusResponseDto> control(
            @JobId @PathVariable(value = "job_id") String jobId,
            @RequestBody JobControlRequestDto jobControlRequestDto) {
        JobStatusResponseDto jobStatus = fileImportService.control(jobId, jobControlRequestDto);

        return ResponseEntity.accepted().body(jobStatus);
    }

    @Operation(
            summary = "Get job status",
            description = "Get the job status by job id",
            parameters = {
                @Parameter(
                        name = "job_id",
                        in = ParameterIn.PATH,
                        description = "Job Id",
                        example = "2025-11-05")
            },
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                JobStatusResponseDto.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Job id is not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GlobalErrorResponse.class)))
            })
    @GetMapping("/{job_id}")
    public ResponseEntity<JobStatusResponseDto> getJobStatusByJobId(
            @JobId @PathVariable(value = "job_id") String jobId) {

        JobStatusResponseDto res = fileImportService.getJobStatus(jobId);

        return ResponseEntity.ok().body(res);
    }

    @Operation(
            summary = "Get file processing status",
            description = "Get file processing status when running the job",
            parameters = {
                @Parameter(
                        name = "job_id",
                        in = ParameterIn.PATH,
                        description = "Job Id",
                        example = "2025-11-05"),
                @Parameter(
                        name = "import_type",
                        in = ParameterIn.PATH,
                        description =
                                "The import type. e.g.: country-zip, city-zip, enterprise-zip, connection-type-zip, anonymous-ip-zip, isp-zip",
                        example = "country-zip")
            },
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful response",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                JobStatusImportTypeResponseDto
                                                                        .class),
                                        examples =
                                                @ExampleObject(
                                                        value =
"""
{
"city_zip": {
"file_name": "location.zip",
"progress_percentage": 12,
"error_message": "org.springframework.dao.DataIntegrityViolationException: could not execute statement",
"step_city_location": {
"status": "FAILED",
"started_at": "2025-12-11T15:09:33.866569",
"finished_at": "2025-12-11T15:09:34.003221",
"exit_message": "org.springframework.dao.DataIntegrityViolationException: could not execute statement "
}
}
}
"""))),
                @ApiResponse(
                        responseCode = "404",
                        description = "The file processing is not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GlobalErrorResponse.class)))
            })
    @GetMapping(value = "/{job_id}/{import_type}")
    public ResponseEntity<JobStatusImportTypeResponseDto> getJobStatusByJobIdAndImportType(
            @JobId @PathVariable(value = "job_id") String jobId,
            @PathVariable(value = "import_type") String importType) {

        JobStatusImportTypeResponseDto res =
                fileImportService.getJobStatusByImportType(jobId, importType);

        return ResponseEntity.ok().body(res);
    }
}
