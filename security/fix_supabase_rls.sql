-- =========================================
-- ARIA SUPABASE RLS SECURITY FIX
-- =========================================
-- This script enables Row Level Security on critical ARIA tables
-- and creates appropriate policies for data protection

-- =========================================
-- 1. ENABLE RLS ON BIAS DETECTION RESULTS
-- =========================================
ALTER TABLE public.bias_detection_results
ENABLE ROW LEVEL SECURITY;

-- =========================================
-- 2. CREATE BIAS DETECTION POLICIES
-- =========================================

-- Policy: Users can only view their own bias results
CREATE POLICY "users_can_view_own_bias_results"
ON public.bias_detection_results
FOR SELECT TO authenticated
USING ((SELECT auth.uid()) = user_id);

-- Policy: Users can only insert their own bias results
CREATE POLICY "users_can_insert_own_bias_results"
ON public.bias_detection_results
FOR INSERT TO authenticated
WITH CHECK ((SELECT auth.uid()) = user_id);

-- Policy: Users can only update their own bias results
CREATE POLICY "users_can_update_own_bias_results"
ON public.bias_detection_results
FOR UPDATE TO authenticated
USING ((SELECT auth.uid()) = user_id)
WITH CHECK ((SELECT auth.uid()) = user_id);

-- Policy: Users can only delete their own bias results
CREATE POLICY "users_can_delete_own_bias_results"
ON public.bias_detection_results
FOR DELETE TO authenticated
USING ((SELECT auth.uid()) = user_id);

-- Policy: Service role can manage all bias results (for AI Analytics Service)
CREATE POLICY "service_can_manage_bias_results"
ON public.bias_detection_results
FOR ALL TO service_role
USING (true)
WITH CHECK (true);

-- Policy: Admins can view all bias results for monitoring
CREATE POLICY "admins_can_view_all_bias_results"
ON public.bias_detection_results
FOR SELECT TO authenticated
USING (
  EXISTS (
    SELECT 1 FROM auth.users 
    WHERE id = auth.uid() 
    AND raw_user_meta_data->>'role' = 'admin'
  )
);

-- =========================================
-- 3. ADD PERFORMANCE INDEX
-- =========================================
CREATE INDEX IF NOT EXISTS idx_bias_detection_results_user_id
ON public.bias_detection_results (user_id);

-- =========================================
-- 4. ENABLE RLS ON OTHER CRITICAL TABLES
-- =========================================

-- Interview Sessions Table
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'interview_sessions' AND table_schema = 'public') THEN
    ALTER TABLE public.interview_sessions ENABLE ROW LEVEL SECURITY;
    
    -- Users can only access their own interview sessions
    CREATE POLICY "users_own_interview_sessions" ON public.interview_sessions
    FOR ALL TO authenticated
    USING ((SELECT auth.uid()) = candidate_id OR (SELECT auth.uid()) = interviewer_id)
    WITH CHECK ((SELECT auth.uid()) = candidate_id OR (SELECT auth.uid()) = interviewer_id);
    
    -- Service role full access
    CREATE POLICY "service_manage_interview_sessions" ON public.interview_sessions
    FOR ALL TO service_role USING (true) WITH CHECK (true);
    
    CREATE INDEX IF NOT EXISTS idx_interview_sessions_candidate_id ON public.interview_sessions (candidate_id);
    CREATE INDEX IF NOT EXISTS idx_interview_sessions_interviewer_id ON public.interview_sessions (interviewer_id);
  END IF;
END $$;

-- Question Responses Table  
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'question_responses' AND table_schema = 'public') THEN
    ALTER TABLE public.question_responses ENABLE ROW LEVEL SECURITY;
    
    -- Users can only access their own responses
    CREATE POLICY "users_own_question_responses" ON public.question_responses
    FOR ALL TO authenticated
    USING ((SELECT auth.uid()) = user_id)
    WITH CHECK ((SELECT auth.uid()) = user_id);
    
    -- Service role full access
    CREATE POLICY "service_manage_question_responses" ON public.question_responses
    FOR ALL TO service_role USING (true) WITH CHECK (true);
    
    CREATE INDEX IF NOT EXISTS idx_question_responses_user_id ON public.question_responses (user_id);
  END IF;
END $$;

-- Performance Analytics Table
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'performance_analytics' AND table_schema = 'public') THEN
    ALTER TABLE public.performance_analytics ENABLE ROW LEVEL SECURITY;
    
    -- Users can only access their own performance data
    CREATE POLICY "users_own_performance_analytics" ON public.performance_analytics
    FOR SELECT TO authenticated
    USING ((SELECT auth.uid()) = user_id);
    
    -- Service role can insert/update performance data
    CREATE POLICY "service_manage_performance_analytics" ON public.performance_analytics
    FOR ALL TO service_role USING (true) WITH CHECK (true);
    
    CREATE INDEX IF NOT EXISTS idx_performance_analytics_user_id ON public.performance_analytics (user_id);
  END IF;
END $$;

-- Speech Transcripts Table
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'speech_transcripts' AND table_schema = 'public') THEN
    ALTER TABLE public.speech_transcripts ENABLE ROW LEVEL SECURITY;
    
    -- Users can only access their own transcripts
    CREATE POLICY "users_own_speech_transcripts" ON public.speech_transcripts
    FOR ALL TO authenticated
    USING ((SELECT auth.uid()) = user_id)
    WITH CHECK ((SELECT auth.uid()) = user_id);
    
    -- Service role full access
    CREATE POLICY "service_manage_speech_transcripts" ON public.speech_transcripts
    FOR ALL TO service_role USING (true) WITH CHECK (true);
    
    CREATE INDEX IF NOT EXISTS idx_speech_transcripts_user_id ON public.speech_transcripts (user_id);
  END IF;
END $$;

-- =========================================
-- 5. VERIFICATION QUERIES
-- =========================================

-- Check which tables now have RLS enabled
SELECT 
  schemaname,
  tablename,
  CASE 
    WHEN c.relhasrowsecurity THEN '✅ RLS ENABLED' 
    ELSE '❌ RLS DISABLED' 
  END as rls_status
FROM pg_tables t
JOIN pg_class c ON c.relname = t.tablename
WHERE t.schemaname = 'public'
AND t.tablename IN (
  'bias_detection_results',
  'interview_sessions', 
  'question_responses',
  'performance_analytics',
  'speech_transcripts'
)
ORDER BY tablename;

-- List all policies created
SELECT 
  schemaname,
  tablename,
  policyname,
  permissive,
  roles,
  cmd
FROM pg_policies
WHERE schemaname = 'public'
ORDER BY tablename, policyname;

-- =========================================
-- 6. POST-DEPLOYMENT TEST QUERIES
-- =========================================

-- Test 1: Verify RLS is working (should return no rows for non-owners)
-- SELECT * FROM bias_detection_results; -- Run as different users

-- Test 2: Verify service role can access everything  
-- SET ROLE service_role;
-- SELECT count(*) FROM bias_detection_results;

-- Test 3: Verify authenticated users can only see their own data
-- SELECT * FROM bias_detection_results WHERE user_id = auth.uid();

COMMIT;
