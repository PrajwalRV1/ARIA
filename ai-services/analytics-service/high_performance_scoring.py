#!/usr/bin/env python3
"""
High-Performance Real-time Scoring Engine for ARIA Interview Platform

Provides millisecond-level response analysis with:
- Performance guarantees (< 500ms response time)
- Concurrent processing capabilities
- Real-time monitoring and fallback mechanisms
- Memory-efficient processing
- Circuit breaker pattern for resilience
"""

import asyncio
import json
import logging
import time
import statistics
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any, Tuple, Callable
from enum import Enum
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FutureTimeoutError
import threading
from collections import defaultdict, deque
import psutil
import gc

# Fast ML/NLP imports
try:
    import numpy as np
    from scipy import stats
    import joblib
    FAST_ML_AVAILABLE = True
except ImportError:
    FAST_ML_AVAILABLE = False
    logging.warning("Fast ML libraries not available - using basic analysis")

# Performance monitoring
try:
    import prometheus_client
    from prometheus_client import Counter, Histogram, Gauge, start_http_server
    PROMETHEUS_AVAILABLE = True
except ImportError:
    PROMETHEUS_AVAILABLE = False
    logging.warning("Prometheus not available - using basic metrics")

logger = logging.getLogger(__name__)

# ==================== PERFORMANCE CONSTANTS ====================

class PerformanceThresholds:
    """Performance SLA thresholds"""
    MAX_RESPONSE_TIME_MS = 500  # Maximum allowed response time
    TARGET_RESPONSE_TIME_MS = 200  # Target response time
    MAX_QUEUE_SIZE = 1000  # Maximum requests in queue
    MAX_CONCURRENT_REQUESTS = 50  # Maximum concurrent processing
    MEMORY_THRESHOLD_MB = 2048  # Memory usage threshold
    CPU_THRESHOLD_PERCENT = 80  # CPU usage threshold

class ProcessingPriority(Enum):
    """Processing priority levels"""
    CRITICAL = 1  # Real-time interview responses
    HIGH = 2      # Code analysis
    MEDIUM = 3    # Quality assessment
    LOW = 4       # Background analytics

# ==================== PERFORMANCE MONITORING ====================

@dataclass
class PerformanceMetrics:
    """Performance metrics tracking"""
    request_id: str
    start_time: float
    end_time: Optional[float] = None
    processing_time_ms: Optional[float] = None
    queue_time_ms: Optional[float] = None
    priority: ProcessingPriority = ProcessingPriority.MEDIUM
    success: bool = True
    error_message: Optional[str] = None
    memory_usage_mb: Optional[float] = None
    cpu_percent: Optional[float] = None

@dataclass
class SystemHealthMetrics:
    """System health and performance tracking"""
    timestamp: datetime
    active_requests: int
    queue_size: int
    average_response_time_ms: float
    p95_response_time_ms: float
    p99_response_time_ms: float
    success_rate: float
    memory_usage_mb: float
    cpu_percent: float
    errors_per_minute: int

# ==================== HIGH-PERFORMANCE PROCESSING ENGINE ====================

class HighPerformanceScoringEngine:
    """
    High-performance scoring engine with millisecond-level guarantees
    """
    
    def __init__(self, 
                 max_workers: int = 20,
                 enable_monitoring: bool = True,
                 enable_circuit_breaker: bool = True):
        
        # Core configuration
        self.max_workers = max_workers
        self.enable_monitoring = enable_monitoring
        self.enable_circuit_breaker = enable_circuit_breaker
        
        # Thread pools for different priorities
        self.critical_executor = ThreadPoolExecutor(
            max_workers=max(4, max_workers // 4),
            thread_name_prefix="critical-scorer"
        )
        self.high_executor = ThreadPoolExecutor(
            max_workers=max(6, max_workers // 3),
            thread_name_prefix="high-scorer"
        )
        self.medium_executor = ThreadPoolExecutor(
            max_workers=max(8, max_workers // 2),
            thread_name_prefix="medium-scorer"
        )
        self.low_executor = ThreadPoolExecutor(
            max_workers=max(2, max_workers // 10),
            thread_name_prefix="low-scorer"
        )
        
        # Performance tracking
        self.performance_history = deque(maxlen=10000)
        self.system_metrics = deque(maxlen=1000)
        self.active_requests = {}
        self.request_counter = 0
        self.lock = threading.RLock()
        
        # Circuit breaker state
        self.circuit_breaker_open = False
        self.circuit_breaker_failures = 0
        self.circuit_breaker_last_failure = None
        self.CIRCUIT_BREAKER_THRESHOLD = 10
        self.CIRCUIT_BREAKER_TIMEOUT = 30  # seconds
        
        # Pre-compiled patterns and models
        self.fast_patterns = self._compile_fast_patterns()
        self.lightweight_models = self._load_lightweight_models()
        
        # Prometheus metrics (if available)
        self.prometheus_metrics = self._setup_prometheus_metrics()
        
        # Background monitoring task
        self.monitoring_task = None
        if enable_monitoring:
            self.monitoring_task = asyncio.create_task(self._monitor_system_health())
        
        logger.info(f"✅ High-performance scoring engine initialized with {max_workers} workers")
    
    def _setup_prometheus_metrics(self) -> Optional[Dict]:
        """Setup Prometheus metrics if available"""
        if not PROMETHEUS_AVAILABLE:
            return None
        
        return {
            'requests_total': Counter(
                'scoring_requests_total',
                'Total scoring requests',
                ['priority', 'status']
            ),
            'request_duration': Histogram(
                'scoring_request_duration_ms',
                'Request processing duration in milliseconds',
                ['priority'],
                buckets=[10, 50, 100, 200, 500, 1000, 2000, 5000]
            ),
            'active_requests': Gauge(
                'scoring_active_requests',
                'Number of active scoring requests'
            ),
            'queue_size': Gauge(
                'scoring_queue_size',
                'Number of requests in queue',
                ['priority']
            ),
            'system_memory': Gauge(
                'scoring_system_memory_mb',
                'System memory usage in MB'
            ),
            'system_cpu': Gauge(
                'scoring_system_cpu_percent',
                'System CPU usage percentage'
            )
        }
    
    def _compile_fast_patterns(self) -> Dict:
        """Pre-compile patterns for fast text analysis"""
        import re
        
        return {
            'technical_keywords': re.compile(
                r'\b(algorithm|optimization|performance|scalability|architecture|database|api|framework)\b',
                re.IGNORECASE
            ),
            'confidence_indicators': re.compile(
                r'\b(definitely|absolutely|clearly|obviously|certainly)\b',
                re.IGNORECASE
            ),
            'uncertainty_indicators': re.compile(
                r'\b(maybe|perhaps|probably|not sure|don\'t know|uncertain)\b',
                re.IGNORECASE
            ),
            'quality_indicators': re.compile(
                r'\b(best practice|optimization|efficient|maintainable|readable|testable)\b',
                re.IGNORECASE
            ),
            'negative_indicators': re.compile(
                r'\b(hack|workaround|temporary|quick fix|dirty|broken)\b',
                re.IGNORECASE
            )
        }
    
    def _load_lightweight_models(self) -> Dict:
        """Load pre-trained lightweight models"""
        models = {}
        
        if FAST_ML_AVAILABLE:
            try:
                # Pre-computed weights for fast scoring
                models['technical_weights'] = np.array([
                    0.25,  # keyword density
                    0.20,  # response length
                    0.15,  # technical depth
                    0.15,  # clarity
                    0.10,  # confidence
                    0.10,  # structure
                    0.05   # innovation
                ])
                
                models['behavioral_weights'] = np.array([
                    0.30,  # STAR format
                    0.25,  # leadership indicators
                    0.20,  # problem-solving approach
                    0.15,  # communication clarity
                    0.10   # outcome focus
                ])
                
                logger.info("✅ Lightweight models loaded successfully")
                
            except Exception as e:
                logger.warning(f"Failed to load lightweight models: {e}")
        
        return models
    
    async def score_response_with_guarantees(
        self,
        session_id: str,
        candidate_id: str,
        question_id: str,
        response_text: str,
        response_metadata: Dict[str, Any],
        question_context: Dict[str, Any],
        priority: ProcessingPriority = ProcessingPriority.HIGH
    ) -> Tuple[Dict[str, Any], PerformanceMetrics]:
        """
        Score response with millisecond-level performance guarantees
        """
        
        request_id = f"req_{self.request_counter}_{int(time.time() * 1000)}"
        self.request_counter += 1
        start_time = time.time()
        
        # Create performance tracking
        metrics = PerformanceMetrics(
            request_id=request_id,
            start_time=start_time,
            priority=priority
        )
        
        try:
            # Check circuit breaker
            if self.circuit_breaker_open and self._should_circuit_breaker_stay_open():
                raise Exception("Circuit breaker is open - too many failures")
            
            # Check system health
            if not await self._check_system_health():
                raise Exception("System overloaded - rejecting request")
            
            # Track active request
            with self.lock:
                self.active_requests[request_id] = {
                    'start_time': start_time,
                    'priority': priority,
                    'session_id': session_id
                }
            
            # Update Prometheus metrics
            if self.prometheus_metrics:
                self.prometheus_metrics['active_requests'].inc()
                self.prometheus_metrics['requests_total'].labels(
                    priority=priority.name, 
                    status='started'
                ).inc()
            
            # Select appropriate executor based on priority
            executor = self._get_executor_for_priority(priority)
            
            # Queue time tracking
            queue_start = time.time()
            
            # Execute scoring with timeout
            future = executor.submit(
                self._perform_fast_scoring,
                session_id, candidate_id, question_id, response_text,
                response_metadata, question_context
            )
            
            try:
                # Wait with timeout based on priority
                timeout = self._get_timeout_for_priority(priority)
                result = await asyncio.wait_for(
                    asyncio.wrap_future(future),
                    timeout=timeout / 1000.0  # Convert to seconds
                )
                
                # Calculate timing
                end_time = time.time()
                processing_time_ms = (end_time - start_time) * 1000
                queue_time_ms = (time.time() - queue_start) * 1000
                
                # Update metrics
                metrics.end_time = end_time
                metrics.processing_time_ms = processing_time_ms
                metrics.queue_time_ms = queue_time_ms
                metrics.success = True
                
                # System resource usage
                metrics.memory_usage_mb = self._get_memory_usage()
                metrics.cpu_percent = self._get_cpu_usage()
                
                # Check SLA compliance
                if processing_time_ms > PerformanceThresholds.MAX_RESPONSE_TIME_MS:
                    logger.warning(f"⚠️ SLA violation: Response took {processing_time_ms:.1f}ms (limit: {PerformanceThresholds.MAX_RESPONSE_TIME_MS}ms)")
                
                # Update Prometheus metrics
                if self.prometheus_metrics:
                    self.prometheus_metrics['request_duration'].labels(
                        priority=priority.name
                    ).observe(processing_time_ms)
                    self.prometheus_metrics['requests_total'].labels(
                        priority=priority.name,
                        status='success'
                    ).inc()
                
                # Reset circuit breaker on success
                if self.circuit_breaker_failures > 0:
                    self.circuit_breaker_failures = max(0, self.circuit_breaker_failures - 1)
                
                logger.debug(f"✅ Response scored in {processing_time_ms:.1f}ms (ID: {request_id})")
                
                return result, metrics
                
            except asyncio.TimeoutError:
                # Handle timeout
                future.cancel()
                raise Exception(f"Processing timeout after {timeout}ms")
                
        except Exception as e:
            # Handle error
            end_time = time.time()
            processing_time_ms = (end_time - start_time) * 1000
            
            metrics.end_time = end_time
            metrics.processing_time_ms = processing_time_ms
            metrics.success = False
            metrics.error_message = str(e)
            
            # Update circuit breaker
            self.circuit_breaker_failures += 1
            self.circuit_breaker_last_failure = time.time()
            
            if (self.circuit_breaker_failures >= self.CIRCUIT_BREAKER_THRESHOLD and
                self.enable_circuit_breaker):
                self.circuit_breaker_open = True
                logger.error(f"🚨 Circuit breaker opened after {self.circuit_breaker_failures} failures")
            
            # Update Prometheus metrics
            if self.prometheus_metrics:
                self.prometheus_metrics['requests_total'].labels(
                    priority=priority.name,
                    status='error'
                ).inc()
            
            logger.error(f"❌ Scoring failed for request {request_id}: {e}")
            
            # Return fallback result
            fallback_result = self._generate_fallback_result(
                question_context, processing_time_ms
            )
            
            return fallback_result, metrics
            
        finally:
            # Cleanup
            with self.lock:
                if request_id in self.active_requests:
                    del self.active_requests[request_id]
            
            # Update metrics
            if self.prometheus_metrics:
                self.prometheus_metrics['active_requests'].dec()
            
            # Store performance data
            self.performance_history.append(metrics)
    
    def _perform_fast_scoring(
        self,
        session_id: str,
        candidate_id: str,
        question_id: str,
        response_text: str,
        response_metadata: Dict[str, Any],
        question_context: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Perform fast scoring using optimized algorithms
        """
        
        start_time = time.time()
        
        # Fast preprocessing
        processed_text = response_text.lower().strip()
        word_count = len(processed_text.split())
        
        # Quick quality checks
        if word_count < 3:
            return self._generate_minimal_result("Response too short", 1.0)
        
        if word_count > 1000:
            # Truncate for performance
            words = processed_text.split()[:1000]
            processed_text = ' '.join(words)
            word_count = 1000
        
        # Fast pattern matching
        scores = {}
        
        # Technical scoring using pre-compiled patterns
        technical_matches = len(self.fast_patterns['technical_keywords'].findall(processed_text))
        confidence_matches = len(self.fast_patterns['confidence_indicators'].findall(processed_text))
        uncertainty_matches = len(self.fast_patterns['uncertainty_indicators'].findall(processed_text))
        quality_matches = len(self.fast_patterns['quality_indicators'].findall(processed_text))
        negative_matches = len(self.fast_patterns['negative_indicators'].findall(processed_text))
        
        # Fast scoring calculation
        if FAST_ML_AVAILABLE and 'technical_weights' in self.lightweight_models:
            # Use pre-computed weights for fast scoring
            features = np.array([
                min(1.0, technical_matches / 5.0),  # normalized technical density
                min(1.0, word_count / 100.0),       # normalized length
                min(1.0, quality_matches / 3.0),    # quality indicators
                min(1.0, confidence_matches / 2.0), # confidence level
                max(0.0, 1.0 - uncertainty_matches / 3.0),  # certainty
                min(1.0, (word_count / 50.0) * 0.5),        # structure proxy
                min(0.3, technical_matches / 10.0)          # innovation proxy
            ])
            
            weights = self.lightweight_models['technical_weights']
            technical_score = float(np.dot(features, weights))
        else:
            # Fallback calculation
            technical_score = (
                min(1.0, technical_matches / 5.0) * 0.4 +
                min(1.0, word_count / 100.0) * 0.3 +
                min(1.0, quality_matches / 3.0) * 0.2 +
                max(0.0, 1.0 - uncertainty_matches / 3.0) * 0.1
            )
        
        # Communication scoring
        sentence_count = max(1, processed_text.count('.') + processed_text.count('!') + processed_text.count('?'))
        avg_sentence_length = word_count / sentence_count
        
        communication_score = (
            min(1.0, word_count / 50.0) * 0.4 +  # adequate length
            min(1.0, 20 / max(1, avg_sentence_length)) * 0.3 +  # readable sentences
            min(1.0, quality_matches / 2.0) * 0.2 +  # clear expressions
            max(0.0, 1.0 - negative_matches / 2.0) * 0.1  # avoid negative language
        )
        
        # Problem-solving scoring
        problem_keywords = ['solution', 'approach', 'method', 'strategy', 'analyze', 'implement']
        problem_matches = sum(1 for kw in problem_keywords if kw in processed_text)
        
        problem_solving_score = (
            min(1.0, problem_matches / 4.0) * 0.5 +
            min(1.0, technical_matches / 3.0) * 0.3 +
            min(1.0, word_count / 80.0) * 0.2
        )
        
        # Convert to 1-5 scale
        scores = {
            'technical_knowledge': max(1.0, min(5.0, 1 + technical_score * 4)),
            'communication': max(1.0, min(5.0, 1 + communication_score * 4)),
            'problem_solving': max(1.0, min(5.0, 1 + problem_solving_score * 4))
        }
        
        # Calculate overall score
        overall_score = statistics.mean(scores.values())
        
        # Calculate confidence based on response characteristics
        confidence = min(1.0, (
            min(1.0, word_count / 30.0) * 0.4 +  # adequate length
            min(1.0, technical_matches / 2.0) * 0.3 +  # technical content
            max(0.3, 1.0 - uncertainty_matches / 2.0) * 0.3  # certainty level
        ))
        
        # Processing time check
        processing_time = (time.time() - start_time) * 1000
        
        # Generate result
        result = {
            'overall_score': overall_score,
            'dimension_scores': scores,
            'confidence': confidence,
            'metadata': {
                'processing_time_ms': processing_time,
                'word_count': word_count,
                'technical_matches': technical_matches,
                'quality_score': min(100, int(overall_score * 20)),
                'analysis_method': 'fast_optimized'
            },
            'performance_metrics': {
                'within_sla': processing_time < PerformanceThresholds.MAX_RESPONSE_TIME_MS,
                'processing_speed': 'optimal' if processing_time < 100 else 'acceptable'
            }
        }
        
        return result
    
    def _generate_fallback_result(self, question_context: Dict, processing_time_ms: float) -> Dict[str, Any]:
        """Generate fallback result when main processing fails"""
        return {
            'overall_score': 2.5,  # Neutral score
            'dimension_scores': {
                'technical_knowledge': 2.5,
                'communication': 2.5,
                'problem_solving': 2.5
            },
            'confidence': 0.1,  # Low confidence for fallback
            'metadata': {
                'processing_time_ms': processing_time_ms,
                'analysis_method': 'fallback',
                'note': 'Fallback result due to processing error'
            },
            'performance_metrics': {
                'within_sla': processing_time_ms < PerformanceThresholds.MAX_RESPONSE_TIME_MS,
                'processing_speed': 'fallback'
            }
        }
    
    def _generate_minimal_result(self, reason: str, score: float) -> Dict[str, Any]:
        """Generate minimal result for edge cases"""
        return {
            'overall_score': score,
            'dimension_scores': {
                'technical_knowledge': score,
                'communication': score,
                'problem_solving': score
            },
            'confidence': 0.2,
            'metadata': {
                'processing_time_ms': 1,  # Minimal processing time
                'analysis_method': 'minimal',
                'reason': reason
            },
            'performance_metrics': {
                'within_sla': True,
                'processing_speed': 'instant'
            }
        }
    
    def _get_executor_for_priority(self, priority: ProcessingPriority) -> ThreadPoolExecutor:
        """Get appropriate thread pool executor for priority level"""
        if priority == ProcessingPriority.CRITICAL:
            return self.critical_executor
        elif priority == ProcessingPriority.HIGH:
            return self.high_executor
        elif priority == ProcessingPriority.MEDIUM:
            return self.medium_executor
        else:
            return self.low_executor
    
    def _get_timeout_for_priority(self, priority: ProcessingPriority) -> int:
        """Get timeout in milliseconds for priority level"""
        if priority == ProcessingPriority.CRITICAL:
            return 200  # 200ms for critical
        elif priority == ProcessingPriority.HIGH:
            return 400  # 400ms for high
        elif priority == ProcessingPriority.MEDIUM:
            return 800  # 800ms for medium
        else:
            return 2000  # 2s for low priority
    
    async def _check_system_health(self) -> bool:
        """Check if system can handle new requests"""
        try:
            # Check active requests
            if len(self.active_requests) >= PerformanceThresholds.MAX_CONCURRENT_REQUESTS:
                return False
            
            # Check memory usage
            memory_mb = self._get_memory_usage()
            if memory_mb > PerformanceThresholds.MEMORY_THRESHOLD_MB:
                return False
            
            # Check CPU usage
            cpu_percent = self._get_cpu_usage()
            if cpu_percent > PerformanceThresholds.CPU_THRESHOLD_PERCENT:
                return False
            
            return True
            
        except Exception as e:
            logger.error(f"Health check failed: {e}")
            return False
    
    def _get_memory_usage(self) -> float:
        """Get current memory usage in MB"""
        try:
            process = psutil.Process()
            memory_info = process.memory_info()
            return memory_info.rss / (1024 * 1024)  # Convert to MB
        except:
            return 0.0
    
    def _get_cpu_usage(self) -> float:
        """Get current CPU usage percentage"""
        try:
            return psutil.cpu_percent(interval=None)
        except:
            return 0.0
    
    def _should_circuit_breaker_stay_open(self) -> bool:
        """Check if circuit breaker should remain open"""
        if not self.circuit_breaker_last_failure:
            return False
        
        time_since_failure = time.time() - self.circuit_breaker_last_failure
        if time_since_failure > self.CIRCUIT_BREAKER_TIMEOUT:
            # Try to close circuit breaker
            self.circuit_breaker_open = False
            self.circuit_breaker_failures = 0
            logger.info("🔄 Circuit breaker closed - attempting recovery")
            return False
        
        return True
    
    async def _monitor_system_health(self):
        """Background task to monitor system health"""
        while True:
            try:
                await asyncio.sleep(10)  # Monitor every 10 seconds
                
                # Collect metrics
                current_time = datetime.now()
                active_count = len(self.active_requests)
                memory_usage = self._get_memory_usage()
                cpu_percent = self._get_cpu_usage()
                
                # Calculate performance metrics from recent history
                recent_metrics = [m for m in self.performance_history 
                                if m.end_time and (time.time() - m.end_time) < 300]  # Last 5 minutes
                
                if recent_metrics:
                    response_times = [m.processing_time_ms for m in recent_metrics if m.processing_time_ms]
                    if response_times:
                        avg_response = statistics.mean(response_times)
                        p95_response = np.percentile(response_times, 95) if FAST_ML_AVAILABLE else max(response_times)
                        p99_response = np.percentile(response_times, 99) if FAST_ML_AVAILABLE else max(response_times)
                    else:
                        avg_response = p95_response = p99_response = 0
                    
                    success_count = sum(1 for m in recent_metrics if m.success)
                    success_rate = success_count / len(recent_metrics) if recent_metrics else 1.0
                    error_count = len(recent_metrics) - success_count
                else:
                    avg_response = p95_response = p99_response = 0
                    success_rate = 1.0
                    error_count = 0
                
                # Create health metrics
                health_metrics = SystemHealthMetrics(
                    timestamp=current_time,
                    active_requests=active_count,
                    queue_size=0,  # Would track actual queue size
                    average_response_time_ms=avg_response,
                    p95_response_time_ms=p95_response,
                    p99_response_time_ms=p99_response,
                    success_rate=success_rate,
                    memory_usage_mb=memory_usage,
                    cpu_percent=cpu_percent,
                    errors_per_minute=error_count
                )
                
                self.system_metrics.append(health_metrics)
                
                # Update Prometheus metrics
                if self.prometheus_metrics:
                    self.prometheus_metrics['system_memory'].set(memory_usage)
                    self.prometheus_metrics['system_cpu'].set(cpu_percent)
                    self.prometheus_metrics['active_requests'].set(active_count)
                
                # Log health status
                if avg_response > PerformanceThresholds.TARGET_RESPONSE_TIME_MS * 2:
                    logger.warning(f"⚠️ High response times: {avg_response:.1f}ms average")
                
                if success_rate < 0.95:
                    logger.warning(f"⚠️ Low success rate: {success_rate:.2%}")
                
                if memory_usage > PerformanceThresholds.MEMORY_THRESHOLD_MB * 0.8:
                    logger.warning(f"⚠️ High memory usage: {memory_usage:.1f}MB")
                    # Trigger garbage collection
                    gc.collect()
                
            except Exception as e:
                logger.error(f"System monitoring error: {e}")
    
    def get_performance_report(self) -> Dict[str, Any]:
        """Get comprehensive performance report"""
        try:
            recent_metrics = [m for m in self.performance_history 
                            if m.end_time and (time.time() - m.end_time) < 3600]  # Last hour
            
            if not recent_metrics:
                return {"status": "no_data", "message": "No recent performance data"}
            
            # Calculate statistics
            response_times = [m.processing_time_ms for m in recent_metrics if m.processing_time_ms]
            success_count = sum(1 for m in recent_metrics if m.success)
            
            report = {
                "timestamp": datetime.now().isoformat(),
                "timeframe": "last_hour",
                "total_requests": len(recent_metrics),
                "successful_requests": success_count,
                "success_rate": success_count / len(recent_metrics) if recent_metrics else 0,
                "response_times": {
                    "average_ms": statistics.mean(response_times) if response_times else 0,
                    "median_ms": statistics.median(response_times) if response_times else 0,
                    "min_ms": min(response_times) if response_times else 0,
                    "max_ms": max(response_times) if response_times else 0,
                    "p95_ms": np.percentile(response_times, 95) if FAST_ML_AVAILABLE and response_times else 0,
                    "p99_ms": np.percentile(response_times, 99) if FAST_ML_AVAILABLE and response_times else 0
                },
                "sla_compliance": {
                    "within_target": sum(1 for t in response_times if t <= PerformanceThresholds.TARGET_RESPONSE_TIME_MS),
                    "within_max": sum(1 for t in response_times if t <= PerformanceThresholds.MAX_RESPONSE_TIME_MS),
                    "target_compliance_rate": sum(1 for t in response_times if t <= PerformanceThresholds.TARGET_RESPONSE_TIME_MS) / len(response_times) if response_times else 0,
                    "max_compliance_rate": sum(1 for t in response_times if t <= PerformanceThresholds.MAX_RESPONSE_TIME_MS) / len(response_times) if response_times else 0
                },
                "system_health": {
                    "active_requests": len(self.active_requests),
                    "circuit_breaker_open": self.circuit_breaker_open,
                    "circuit_breaker_failures": self.circuit_breaker_failures,
                    "memory_usage_mb": self._get_memory_usage(),
                    "cpu_percent": self._get_cpu_usage()
                }
            }
            
            return report
            
        except Exception as e:
            logger.error(f"Error generating performance report: {e}")
            return {"status": "error", "message": str(e)}
    
    def reset_circuit_breaker(self) -> bool:
        """Manually reset circuit breaker"""
        try:
            self.circuit_breaker_open = False
            self.circuit_breaker_failures = 0
            self.circuit_breaker_last_failure = None
            logger.info("🔄 Circuit breaker manually reset")
            return True
        except Exception as e:
            logger.error(f"Failed to reset circuit breaker: {e}")
            return False
    
    def cleanup(self):
        """Cleanup resources"""
        try:
            if self.monitoring_task:
                self.monitoring_task.cancel()
            
            # Shutdown executors
            self.critical_executor.shutdown(wait=True)
            self.high_executor.shutdown(wait=True)
            self.medium_executor.shutdown(wait=True)
            self.low_executor.shutdown(wait=True)
            
            logger.info("✅ High-performance scoring engine cleaned up")
            
        except Exception as e:
            logger.error(f"Error during cleanup: {e}")

# ==================== WEBSOCKET HANDLER FOR REAL-TIME SCORING ====================

class HighPerformanceScoringWebSocket:
    """WebSocket handler for real-time high-performance scoring"""
    
    def __init__(self, scoring_engine: HighPerformanceScoringEngine):
        self.scoring_engine = scoring_engine
        self.active_connections: Dict[str, List] = {}
        
    async def connect(self, websocket, session_id: str, priority: str = 'high'):
        """Connect to high-performance scoring"""
        if session_id not in self.active_connections:
            self.active_connections[session_id] = []
        
        self.active_connections[session_id].append({
            'websocket': websocket,
            'priority': ProcessingPriority[priority.upper()],
            'connected_at': datetime.now()
        })
        
        # Send performance capabilities
        await websocket.send_json({
            "type": "performance_config",
            "max_response_time_ms": PerformanceThresholds.MAX_RESPONSE_TIME_MS,
            "target_response_time_ms": PerformanceThresholds.TARGET_RESPONSE_TIME_MS,
            "capabilities": ["real_time_scoring", "performance_monitoring", "circuit_breaker"]
        })
        
    async def disconnect(self, websocket, session_id: str):
        """Disconnect from high-performance scoring"""
        if session_id in self.active_connections:
            self.active_connections[session_id] = [
                conn for conn in self.active_connections[session_id]
                if conn['websocket'] != websocket
            ]
            if not self.active_connections[session_id]:
                del self.active_connections[session_id]
    
    async def handle_scoring_request(self, websocket, session_id: str, request_data: Dict[str, Any]):
        """Handle high-performance scoring request"""
        try:
            # Extract request data
            candidate_id = request_data.get('candidate_id')
            question_id = request_data.get('question_id')
            response_text = request_data.get('response_text', '')
            response_metadata = request_data.get('metadata', {})
            question_context = request_data.get('question_context', {})
            priority_str = request_data.get('priority', 'high')
            
            # Convert priority
            try:
                priority = ProcessingPriority[priority_str.upper()]
            except KeyError:
                priority = ProcessingPriority.HIGH
            
            # Perform high-performance scoring
            result, metrics = await self.scoring_engine.score_response_with_guarantees(
                session_id=session_id,
                candidate_id=candidate_id,
                question_id=question_id,
                response_text=response_text,
                response_metadata=response_metadata,
                question_context=question_context,
                priority=priority
            )
            
            # Send response
            response = {
                "type": "scoring_result",
                "session_id": session_id,
                "request_id": metrics.request_id,
                "result": result,
                "performance": {
                    "processing_time_ms": metrics.processing_time_ms,
                    "within_sla": metrics.processing_time_ms <= PerformanceThresholds.MAX_RESPONSE_TIME_MS if metrics.processing_time_ms else False,
                    "priority": priority.name,
                    "success": metrics.success
                },
                "timestamp": datetime.now().isoformat()
            }
            
            await websocket.send_json(response)
            
        except Exception as e:
            error_response = {
                "type": "scoring_error",
                "session_id": session_id,
                "error": str(e),
                "timestamp": datetime.now().isoformat()
            }
            await websocket.send_json(error_response)

# Example usage
if __name__ == "__main__":
    import asyncio
    
    async def test_high_performance_scoring():
        """Test the high-performance scoring system"""
        
        # Initialize engine
        engine = HighPerformanceScoringEngine(max_workers=10)
        
        # Test data
        test_response = """
        To implement a scalable microservices architecture, I would start by identifying 
        the core business domains and designing services around them. Each service should 
        have its own database and communicate through well-defined APIs. I'd use 
        containerization with Docker and orchestration with Kubernetes for deployment. 
        For performance, I'd implement caching strategies, load balancing, and monitoring.
        """
        
        test_metadata = {
            "response_time_seconds": 45,
            "word_count": len(test_response.split())
        }
        
        test_context = {
            "question_type": "technical",
            "job_role": "Backend Engineer",
            "experience_level": 3
        }
        
        # Perform scoring
        print("🔬 Testing high-performance scoring...")
        
        start_time = time.time()
        result, metrics = await engine.score_response_with_guarantees(
            session_id="test_session",
            candidate_id="test_candidate",
            question_id="test_question",
            response_text=test_response,
            response_metadata=test_metadata,
            question_context=test_context,
            priority=ProcessingPriority.HIGH
        )
        end_time = time.time()
        
        print(f"✅ Scoring completed in {(end_time - start_time) * 1000:.1f}ms")
        print(f"📊 Overall score: {result['overall_score']:.2f}")
        print(f"🎯 Within SLA: {metrics.processing_time_ms <= PerformanceThresholds.MAX_RESPONSE_TIME_MS}")
        print(f"📈 Performance report:")
        
        # Generate performance report
        await asyncio.sleep(1)  # Let monitoring collect data
        report = engine.get_performance_report()
        print(json.dumps(report, indent=2))
        
        # Cleanup
        engine.cleanup()
    
    # Run test
    asyncio.run(test_high_performance_scoring())
