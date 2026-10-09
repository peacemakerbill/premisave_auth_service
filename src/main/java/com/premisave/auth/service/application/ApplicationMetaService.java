package com.premisave.auth.service.application;

import com.premisave.auth.dto.application.ApplicationDtos.CountryOption;
import com.premisave.auth.dto.application.ApplicationDtos.DocumentTypeOption;
import com.premisave.auth.dto.application.ApplicationDtos.Limits;
import com.premisave.auth.dto.application.ApplicationDtos.MetaResponse;
import com.premisave.auth.dto.application.ApplicationDtos.Option;
import com.premisave.auth.dto.application.ApplicationDtos.RequirementOption;
import com.premisave.auth.enums.ApplicationStatus;
import com.premisave.auth.enums.DocumentType;
import com.premisave.auth.enums.IdType;
import com.premisave.auth.enums.ManagementPreference;
import com.premisave.auth.enums.OwnerType;
import com.premisave.auth.enums.PayoutMethod;
import com.premisave.auth.enums.PropertyType;
import com.premisave.auth.enums.ReferralSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a form needs to render itself: dropdown options, the document
 * checklist per owner type, upload limits. The frontend never hardcodes these.
 */
@Service
public class ApplicationMetaService {

    /** All ISO 3166-1 countries with their English names, sorted by name. */
    static final List<CountryOption> COUNTRIES = Arrays.stream(Locale.getISOCountries())
            .map(code -> new CountryOption(code, Locale.of("", code).getDisplayCountry(Locale.ENGLISH)))
            .sorted(Comparator.comparing(CountryOption::name, String.CASE_INSENSITIVE_ORDER))
            .toList();

    private final ApplicationMapper mapper;
    private final long maxFileBytes;
    private final String maxFileLabel;
    private final int maxDocuments;

    public ApplicationMetaService(ApplicationMapper mapper,
                                  @Value("${home-owner-application.max-file-size-mb:8}") int maxFileSizeMb,
                                  @Value("${home-owner-application.max-documents:25}") int maxDocuments) {
        this.mapper = mapper;
        this.maxFileBytes = maxFileSizeMb * 1024L * 1024L;
        this.maxFileLabel = maxFileSizeMb + "MB";
        this.maxDocuments = maxDocuments;
    }

    public MetaResponse meta() {
        Map<OwnerType, List<RequirementOption>> requirements = new EnumMap<>(OwnerType.class);
        for (OwnerType type : OwnerType.values()) {
            requirements.put(type, ApplicationRequirements.forOwnerType(type).stream()
                    .map(r -> new RequirementOption(r.key(), r.label(), r.description(), r.required(), r.combinations()))
                    .toList());
        }

        return new MetaResponse(
                Arrays.stream(OwnerType.values())
                        .map(o -> new Option(o.name(), o.getLabel(), o.getDescription())).toList(),
                Arrays.stream(IdType.values())
                        .map(o -> new Option(o.name(), o.getLabel(), null)).toList(),
                Arrays.stream(PropertyType.values())
                        .map(o -> new Option(o.name(), o.getLabel(), null)).toList(),
                Arrays.stream(ManagementPreference.values())
                        .map(o -> new Option(o.name(), o.getLabel(), null)).toList(),
                Arrays.stream(PayoutMethod.values())
                        .map(o -> new Option(o.name(), o.getLabel(), null)).toList(),
                Arrays.stream(ReferralSource.values())
                        .map(o -> new Option(o.name(), o.getLabel(), null)).toList(),
                Arrays.stream(ApplicationStatus.values())
                        .map(o -> new Option(o.name(), o.getLabel(), o.getDescription())).toList(),
                Arrays.stream(DocumentType.values())
                        .map(t -> new DocumentTypeOption(t, t.getLabel(), t.getDescription(), t.getCategory(),
                                t.isAllowsMultiple(), t.maxFiles())).toList(),
                requirements,
                COUNTRIES,
                new Limits(maxFileBytes, maxFileLabel, ApplicationFileValidator.ALLOWED_MIME_TYPES,
                        ApplicationFileValidator.ALLOWED_EXTENSIONS, maxDocuments),
                mapper.reviewTimeHint());
    }
}