package com.github.lauroschuck.ankiquickadd.model;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Language {
    AF,
    AM,
    AR,
    AZ,
    BE,
    BG,
    BN,
    BS,
    CA,
    CS(true),
    CY,
    DA,
    DE(true),
    EL(true),
    EN(true),
    ES,
    ET,
    EU,
    FA,
    FI,
    FR(true),
    FY,
    GA,
    GD,
    GL,
    GN,
    GU,
    HA,
    HE,
    HI,
    HR,
    HU,
    HY,
    ID(true),
    IG,
    IS,
    IT(true),
    IW,
    JA(true),
    KA,
    KM,
    KN,
    KO(true),
    KY,
    LB,
    LN,
    LO,
    LT,
    LV,
    MK,
    ML,
    MN,
    MR,
    MS,
    MT,
    MY,
    NB,
    NE,
    NL(true),
    NO,
    OR,
    PA,
    PL(true),
    PT(true),
    RO,
    RU(true),
    SK,
    SL,
    SO,
    SQ,
    SR,
    SV,
    SW,
    TA,
    TE,
    TG,
    TH,
    TL,
    TR(true),
    UK,
    UR,
    UZ,
    VI(true),
    ZH(true),
    ZU;

    private final Locale locale;
    private final boolean availableAsNative;

    Language() {
        this(false);
    }

    Language(boolean availableAsNative) {
        this.availableAsNative = availableAsNative;
        locale = new Locale(getIsoCode());
    }

    public static Language ofIsoCode(String isoCode) {
        return Language.valueOf(isoCode.toUpperCase(Locale.US));
    }

    public static Language[] valuesAvailableAsNative() {
        return Stream.of(values())
                .filter(l -> l.availableAsNative)
                .collect(Collectors.toList())
                .toArray(new Language[0]);
    }

    public String getDisplayName() {
        return locale.getDisplayLanguage(Locale.US);
    }

    public String getIsoCode() {
        return name().toLowerCase(Locale.US);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
