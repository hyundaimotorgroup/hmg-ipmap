package com.hmg.ipmap.iplocation;

import com.hmg.ipmap.common.enums.Scope;
import java.util.Optional;

public record IpLocationResult(String body, boolean notFound, Optional<Scope> scope) {}
