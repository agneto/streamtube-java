/**
 * Infrastructure layer — adapters implementing application/domain ports.
 *
 * <p>Houses JPA persistence models + repositories, Flyway migrations wiring, and (from
 * later phases) the S3 storage adapter, RabbitMQ messaging, and email. Persistence
 * models live here and are mapped to/from the pure domain entities, so the domain never
 * carries framework annotations.
 */
package com.streamtube.infrastructure;
