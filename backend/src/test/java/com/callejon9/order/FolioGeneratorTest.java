package com.callejon9.order;

import com.callejon9.order.service.FolioGenerator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FolioGenerator")
class FolioGeneratorTest {

    @Test
    @DisplayName("respeta el formato ORD-yyMMddHHmmss")
    void followsTheExpectedFormat() {
        String folio = new FolioGenerator().next();

        assertThat(folio).matches("ORD-\\d{12}");
    }

    @Test
    @DisplayName("nunca repite un folio, incluso llamado muchas veces en el mismo instante")
    void neverRepeatsAFolioUnderBurstTraffic() {
        FolioGenerator generator = new FolioGenerator();

        Set<String> folios = new HashSet<>();
        IntStream.range(0, 5_000).forEach(i -> folios.add(generator.next()));

        assertThat(folios).hasSize(5_000);
    }
}
