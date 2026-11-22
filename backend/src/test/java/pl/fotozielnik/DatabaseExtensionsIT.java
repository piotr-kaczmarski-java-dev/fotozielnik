package pl.fotozielnik;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** ADR-0008: the database image and V2 migration provide PostGIS, pgvector and pg_trgm. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DatabaseExtensionsIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void requiredExtensionsAreInstalled() {
        var installed = jdbc.queryForList("SELECT extname FROM pg_extension", String.class);
        assertThat(installed).contains("postgis", "vector", "pg_trgm");
    }

    @Test
    void postgisMeasuresDistanceInMetres() {
        // Warsaw Palace of Culture -> Royal Castle, roughly 2 km
        Double metres = jdbc.queryForObject(
                "SELECT ST_Distance('SRID=4326;POINT(21.0067 52.2319)'::geography,"
                        + " 'SRID=4326;POINT(21.0145 52.2479)'::geography)",
                Double.class);
        assertThat(metres).isBetween(1_500.0, 2_500.0);
    }

    @Test
    void pgvectorComputesCosineDistance() {
        Double distance = jdbc.queryForObject("SELECT '[1,0,0]'::vector <=> '[0,1,0]'::vector", Double.class);
        assertThat(distance).isEqualTo(1.0);
    }

    @Test
    void trigramSimilarityMatchesTypos() {
        Double similarity =
                jdbc.queryForObject("SELECT similarity('Pulsatilla patens', 'Pulsatila patens')", Double.class);
        assertThat(similarity).isGreaterThan(0.5);
    }
}
