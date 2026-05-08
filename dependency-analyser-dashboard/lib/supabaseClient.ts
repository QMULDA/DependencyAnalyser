import { createClient } from '@supabase/supabase-js';

// Fallbacks prevent createClient from throwing during Next.js SSR pre-render at build time.
// All actual data fetching happens inside useEffect (client-side only), so placeholders are never used.
export const supabase = createClient(
  process.env.NEXT_PUBLIC_SUPABASE_URL ?? 'https://placeholder.supabase.co',
  process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? 'placeholder'
);
