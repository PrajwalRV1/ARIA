import { Injectable } from '@angular/core';
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpResponse,
  HttpEvent
} from '@angular/common/http';
import { Observable, of, share } from 'rxjs';
import { tap, finalize } from 'rxjs/operators';

interface CacheEntry {
  response: HttpResponse<any>;
  timestamp: number;
  ttl: number;
}

interface OngoingRequest {
  observable: Observable<HttpEvent<any>>;
  timestamp: number;
}

@Injectable()
export class PerformanceInterceptor implements HttpInterceptor {
  private cache = new Map<string, CacheEntry>();
  private ongoingRequests = new Map<string, OngoingRequest>();
  private readonly DEFAULT_CACHE_TTL = 5 * 60 * 1000; // 5 minutes
  private readonly REQUEST_TIMEOUT = 30 * 1000; // 30 seconds

  // Cache configuration for different endpoints
  private readonly cacheConfig = new Map<RegExp, number>([
    [/\/candidates$/, 2 * 60 * 1000], // Candidate list: 2 minutes
    [/\/candidates\/\d+$/, 10 * 60 * 1000], // Individual candidate: 10 minutes
    [/\/candidates\/search/, 30 * 1000], // Search results: 30 seconds
    [/\/candidates\/by-status/, 1 * 60 * 1000], // Status-based lists: 1 minute
    [/\/interview-round-options/, 60 * 60 * 1000], // Static options: 1 hour
  ]);

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    // Only optimize GET requests for specific endpoints
    if (req.method !== 'GET' || !this.shouldCache(req.url)) {
      return next.handle(req);
    }

    const cacheKey = this.getCacheKey(req);

    // Check for cached response
    const cachedResponse = this.getFromCache(cacheKey);
    if (cachedResponse) {
      console.log(`[PERFORMANCE] Cache HIT for ${req.url}`);
      return of(cachedResponse);
    }

    // Check for ongoing request (deduplication)
    const ongoingRequest = this.ongoingRequests.get(cacheKey);
    if (ongoingRequest && this.isRequestValid(ongoingRequest.timestamp)) {
      console.log(`[PERFORMANCE] Request deduplication for ${req.url}`);
      return ongoingRequest.observable;
    }

    // Make new request with performance optimizations
    const optimizedReq = this.addPerformanceHeaders(req);
    
    const request$ = next.handle(optimizedReq).pipe(
      tap(event => {
        if (event instanceof HttpResponse) {
          this.cacheResponse(cacheKey, event);
        }
      }),
      finalize(() => {
        this.ongoingRequests.delete(cacheKey);
      }),
      share() // Share the observable among multiple subscribers
    );

    // Store ongoing request
    this.ongoingRequests.set(cacheKey, {
      observable: request$,
      timestamp: Date.now()
    });

    console.log(`[PERFORMANCE] New request for ${req.url}`);
    return request$;
  }

  private shouldCache(url: string): boolean {
    // Cache GET requests for API endpoints
    return url.includes('/api/') && 
           (url.includes('/candidates') || 
            url.includes('/interview-round-options') ||
            url.includes('/requisition'));
  }

  private getCacheKey(req: HttpRequest<any>): string {
    // Create unique cache key including auth headers for user-specific data
    const authHeader = req.headers.get('Authorization') || '';
    const userContext = authHeader ? btoa(authHeader).substring(0, 10) : 'anonymous';
    return `${req.method}:${req.url}:${userContext}`;
  }

  private getFromCache(key: string): HttpResponse<any> | null {
    const entry = this.cache.get(key);
    if (!entry) {
      return null;
    }

    const now = Date.now();
    if (now - entry.timestamp > entry.ttl) {
      this.cache.delete(key);
      return null;
    }

    return entry.response.clone();
  }

  private cacheResponse(key: string, response: HttpResponse<any>): void {
    // Only cache successful responses
    if (response.status >= 200 && response.status < 300) {
      const url = key.split(':')[1];
      const ttl = this.getCacheTtl(url);

      this.cache.set(key, {
        response: response.clone(),
        timestamp: Date.now(),
        ttl
      });

      // Clean up old entries periodically
      if (this.cache.size > 100) {
        this.cleanCache();
      }
    }
  }

  private getCacheTtl(url: string): number {
    for (const [pattern, ttl] of this.cacheConfig) {
      if (pattern.test(url)) {
        return ttl;
      }
    }
    return this.DEFAULT_CACHE_TTL;
  }

  private addPerformanceHeaders(req: HttpRequest<any>): HttpRequest<any> {
    return req.clone({
      setHeaders: {
        'Cache-Control': 'no-cache, must-revalidate',
        'Pragma': 'no-cache',
        'Accept-Encoding': 'gzip, deflate, br',
        'Connection': 'keep-alive'
      }
    });
  }

  private isRequestValid(timestamp: number): boolean {
    return Date.now() - timestamp < this.REQUEST_TIMEOUT;
  }

  private cleanCache(): void {
    const now = Date.now();
    const expiredKeys: string[] = [];

    for (const [key, entry] of this.cache) {
      if (now - entry.timestamp > entry.ttl) {
        expiredKeys.push(key);
      }
    }

    expiredKeys.forEach(key => this.cache.delete(key));
    console.log(`[PERFORMANCE] Cleaned ${expiredKeys.length} expired cache entries`);
  }

  // Public method to clear cache when needed (e.g., after create/update operations)
  public clearCache(pattern?: string): void {
    if (!pattern) {
      this.cache.clear();
      console.log('[PERFORMANCE] Cleared all cache entries');
      return;
    }

    const keysToDelete: string[] = [];
    for (const key of this.cache.keys()) {
      if (key.includes(pattern)) {
        keysToDelete.push(key);
      }
    }

    keysToDelete.forEach(key => this.cache.delete(key));
    console.log(`[PERFORMANCE] Cleared ${keysToDelete.length} cache entries matching pattern: ${pattern}`);
  }
}
