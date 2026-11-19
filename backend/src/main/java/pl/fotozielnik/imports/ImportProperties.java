package pl.fotozielnik.imports;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/** Limits for uploads (UC-01). */
@ConfigurationProperties("fotozielnik.imports")
record ImportProperties(@DefaultValue("200MB") DataSize maxFileSize) {}
