package com.hmg.ipmap.location;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Composite primary key for {@link LocationNameEntity}, combining a location foreign key and a
 * locale code.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class LocationNameId implements Serializable {
    /** Foreign key referencing the parent location row. */
    @Column(name = "location_id")
    private Long locationId;

    /** BCP 47 / ISO 639 locale code (e.g. {@code "en"}, {@code "de"}). */
    @Column(name = "locale_code")
    private String localeCode;
}
