package com.example.BackendArchitectureLab.Service.Impl;

import com.example.BackendArchitectureLab.DataAccess.ICompanyDataAccess;
import com.example.BackendArchitectureLab.Vo.Cache.CacheListWrapper;
import com.example.BackendArchitectureLab.Vo.CompanyVo;
import com.example.BackendArchitectureLab.Vo.Common.PageResult;
import com.example.BackendArchitectureLab.Vo.CreateCompanyRequest;
import com.example.BackendArchitectureLab.Vo.Search.CompanySearchQuery;
import com.example.BackendArchitectureLab.Vo.UpdateCompanyRequest;
import com.example.BackendArchitectureLab.Entity.Company;
import com.example.BackendArchitectureLab.Mapper.CompanyMapper;
import com.example.BackendArchitectureLab.Service.ICompanyService;
import com.example.BackendArchitectureLab.Util.SearchSortPolicy;
import com.example.BackendArchitectureLab.Util.TransactionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyService implements ICompanyService {

    private static final SearchSortPolicy SEARCH_SORT_POLICY = new SearchSortPolicy(
            "id", "name", "description", "lastScrapedAt",
            "createdBy", "updatedBy", "createdTime", "updatedTime"
    );

    private final TransactionExecutor transactionExecutor;
    private final ICompanyDataAccess companyDataAccess;
    private final CompanyMapper companyMapper;
    private final CacheManager cacheManager;

    @Override
    @Transactional
    @CacheEvict(value = "companies", key = "'all'")
    public CompanyVo createCompany(CreateCompanyRequest request) {
        Company company = new Company();
        company.setName(request.getName());
        company.setDescription(request.getDescription());
        if (request.getWebsites() != null) {
            for (String url : request.getWebsites()) {
                company.addWebsite(url);
            }
        }
        company = companyDataAccess.save(company);
        return companyMapper.toVo(company);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyVo> getAllCompanies() {
        return getAllCompaniesCache().getData();
    }

    @Override
    @Cacheable(value = "companies", key = "'all'", sync = true)
    public CacheListWrapper<CompanyVo> getAllCompaniesCache() {
        return transactionExecutor.executeReadOnly(() -> {
            List<CompanyVo> list = companyDataAccess.findAll().stream()
                    .map(companyMapper::toVo)
                    .toList();
            return new CacheListWrapper<>(list);
        });
    }

    @Override
    @Cacheable(value = "companies", key = "#id", sync = true)
    public CompanyVo getCompanyById(String id) {
        return transactionExecutor.executeReadOnly(() -> {
            UUID uuid = mapUuid(id);
            if (uuid == null) {
                throw new IllegalArgumentException("ID must not be null");
            }
            Company company = companyDataAccess.findById(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Company not found"));
            return companyMapper.toVo(company);
        });
    }

    @Override
    @Transactional
    @Caching(put = {
        @CachePut(value = "companies", key = "#request.id")
    }, evict = {
        @CacheEvict(value = "companies", key = "'all'")
    })
    public CompanyVo updateCompany(UpdateCompanyRequest request) {
        if (request.getId() == null) {
            throw new IllegalArgumentException("ID must not be null");
        }
        Company company = companyDataAccess.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        if (request.getName() != null) {
            company.setName(request.getName());
        }
        if (request.getWebsites() != null) {
            company.getWebsites().clear();
            for (String url : request.getWebsites()) {
                company.addWebsite(url);
            }
        }
        if (request.getDescription() != null) {
            company.setDescription(request.getDescription());
        }
        company = companyDataAccess.save(company);
        return companyMapper.toVo(company);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "companies", key = "#id"),
        @CacheEvict(value = "companies", key = "'all'")
    })
    public void deleteCompany(String id) {
        UUID uuid = mapUuid(id);
        if (uuid == null) {
            throw new IllegalArgumentException("ID must not be null");
        }
        if (!companyDataAccess.existsById(uuid)) {
            throw new IllegalArgumentException("Company not found");
        }
        companyDataAccess.deleteById(uuid);
    }

    @Override
    public PageResult<CompanyVo> searchCompanies(CompanySearchQuery query) {
        return transactionExecutor.executeReadOnly(() -> {
            SEARCH_SORT_POLICY.validate(query.getSortBy(), query.getSortDir());

            Page<Company> companyPage = companyDataAccess.searchCompanies(query);

            List<CompanyVo> companyVos = companyPage.getContent().stream()
                    .map(companyMapper::toVo)
                    .toList();

            return PageResult.of(companyPage, companyVos);
        });
    }

    @Override
    public Flux<ServerSentEvent<List<CompanyVo>>> streamCompaniesChunked(int chunkSize) {
        final int effectiveChunkSize = Math.max(10, Math.min(chunkSize, 1000));

        Flux<ServerSentEvent<List<CompanyVo>>> dataFlux = Flux.<ServerSentEvent<List<CompanyVo>>, Integer>generate(
                () -> 0,
                (page, sink) -> {
                    try {
                        Page<CompanyVo> companyPage = transactionExecutor.executeReadOnly(() -> {
                            Page<Company> entityPage = companyDataAccess.findAllPaged(
                                    PageRequest.of(page, effectiveChunkSize, Sort.by("id").ascending())
                            );

                            if (entityPage.isEmpty()) {
                                return new PageImpl<>(List.<CompanyVo>of(), entityPage.getPageable(), entityPage.getTotalElements());
                            }

                            List<CompanyVo> voList = entityPage.getContent().stream()
                                    .map(companyMapper::toVo)
                                    .toList();

                            return new PageImpl<>(voList, entityPage.getPageable(), entityPage.getTotalElements());
                        });

                        if (companyPage.isEmpty()) {
                            sink.complete();
                            return page;
                        }

                        sink.next(ServerSentEvent.<List<CompanyVo>>builder()
                                .event("chunk")
                                .id(String.valueOf(page))
                                .data(companyPage.getContent())
                                .build());

                        if (!companyPage.hasNext()) {
                            sink.complete();
                            return page;
                        }

                        return page + 1;
                    } catch (Exception ex) {
                        log.error("公司資料 SSE 分塊串流分頁查詢失敗 [page={}]: {}", page, ex.getMessage(), ex);
                        sink.error(ex);
                        return page;
                    }
                }
        ).subscribeOn(Schedulers.boundedElastic());

        ServerSentEvent<List<CompanyVo>> completeEvent = ServerSentEvent.<List<CompanyVo>>builder()
                .event("complete")
                .data(List.of())
                .build();

        Flux<ServerSentEvent<List<CompanyVo>>> streamWithComplete = Flux.concat(dataFlux, Flux.just(completeEvent));

        Flux<ServerSentEvent<List<CompanyVo>>> heartbeatFlux = Flux.interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.<List<CompanyVo>>builder()
                        .comment("keep-alive")
                        .build());

        return Flux.merge(streamWithComplete, heartbeatFlux)
                .takeUntil(event -> "complete".equals(event.event()))
                .doOnCancel(() -> log.info("客戶端中斷公司資料 SSE 串流連線"))
                .onErrorResume(ex -> {
                    log.error("公司資料 SSE 串流處理發生異常: {}", ex.getMessage(), ex);
                    return Flux.just(
                            ServerSentEvent.<List<CompanyVo>>builder()
                                    .event("error")
                                    .comment("串流處理發生異常，請聯繫管理員")
                                    .build()
                    );
                });
    }

    private UUID mapUuid(String id) {
        return id == null || id.isBlank() ? null : UUID.fromString(id);
    }
}
