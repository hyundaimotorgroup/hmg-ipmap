package com.hmg.ipmap.ipmapping;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public interface IpSpanService {
    /**
     * Replaces all IP spans for the given mapping by deleting the existing records and inserting
     * freshly parsed ones derived from the mapping's current IP notation.
     *
     * @param ipMappingEntity the IP mapping entity whose spans should be refreshed
     */
    @Transactional
    void updateIpSpans(IpMappingEntity ipMappingEntity);

    /**
     * Converts the IP notation of {@code ipMapping} into a list of {@link IpSpanEntity} objects
     * without persisting them.
     *
     * <p>Behaviour per notation type:
     *
     * <ul>
     *   <li>{@code ARRAY} – one entity per address in the comma-separated list
     *   <li>{@code SINGLE} – one entity for the single address (lower == upper)
     *   <li>{@code CIDR}, {@code WILDCARD}, {@code RANGE} – one entity per subnet at the configured
     *       prefix length
     * </ul>
     *
     * @param ipMapping the IP mapping entity providing the notation and scope
     * @return list of unsaved IP span entities derived from the notation
     * @throws UnsupportedOperationException if the notation type is unrecognised
     */
    List<IpSpanEntity> parseNotationToIpSpanList(IpMappingEntity ipMapping);

    /**
     * Deletes all IP spans associated with the given IP mapping.
     *
     * <p>Provided as a single-responsibility entry point so callers (e.g. the delete flow in {@code
     * IpMappingService}) do not need a direct dependency on {@link IpSpanRepository}.
     *
     * @param ipMappingEntity the IP mapping entity whose spans should be removed
     */
    @Transactional
    void deleteAllByIpMapping(IpMappingEntity ipMappingEntity);

    /**
     * Rebuilds (deletes and recreates) IP spans in bulk for the given list of IP mappings. Errors
     * for individual mappings are logged and do not abort the remaining rebuilds.
     *
     * @param ipMappings the IP mapping entities to rebuild spans for
     */
    @Transactional
    void rebuildIpSpans(List<IpMappingEntity> ipMappings);
}
