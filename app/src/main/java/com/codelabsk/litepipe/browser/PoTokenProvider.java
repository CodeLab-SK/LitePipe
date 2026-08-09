package com.codelabsk.litepipe.browser;

import androidx.annotation.NonNull;
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult;

public interface PoTokenProvider {
    void setPoToken(@NonNull PoTokenResult poToken);
}
