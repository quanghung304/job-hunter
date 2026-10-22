import { http, HttpResponse } from 'msw'
import type { ProfileData, ProfileView } from '@/api/queries'
import { server } from './server'

/** Shaped like `GET /api/profile` for the committed example profile. */
export function profileFixture(): ProfileData {
  return {
    id: 'hugh',
    summaryLanguage: 'vi',
    candidate: {
      headline: 'Backend developer, Java and Spring Boot',
      yearsExperience: 4,
      residenceCountry: 'IT',
      city: 'Milano',
      timezone: 'Europe/Rome',
      languages: [
        { code: 'vi', level: 'NATIVE' },
        { code: 'en', level: 'C1' },
      ],
      summary: 'Four years of backend work.\nJava, Spring Boot, SQL.',
      skills: { core: ['Java', 'Spring Boot'], secondary: ['Kotlin'], learning: [] },
      domains: ['banking'],
    },
    preferences: {
      employmentTypes: ['PART_TIME', 'CONTRACT', 'FREELANCE'],
      maxHoursPerWeek: null,
      acceptRegions: ['EU', 'WORLDWIDE'],
      timezone: { maxOffsetDiffHours: 6 },
      minSalary: { amount: 35, currency: 'EUR', period: 'HOUR' },
      seniority: ['JUNIOR', 'MID', 'SENIOR'],
      excludeCompanies: [],
    },
    prefilter: {
      maxAgeDays: 21,
      titleInclude: ['\\bjava\\b', '\\bspring\\b'],
      titleExclude: ['\\b(staff|principal)\\b'],
      minSkillHits: 2,
    },
    notify: { digestMinScore: 65 },
  }
}

export const HASH_1 = '3f9c1a' + 'a'.repeat(58)
export const HASH_2 = '7b20e4' + 'b'.repeat(58)

export interface ProfileApi {
  /** What GET answers; a successful PUT replaces it (new hash, sent profile). */
  current: ProfileView
  puts: { baseHash: string; profile: ProfileData }[]
  /** Answer the next PUT with this Problem Details instead of saving. */
  failNext?: { status: number; body: Record<string, unknown> }
}

export function mockProfileApi(): ProfileApi {
  const api: ProfileApi = {
    current: { id: 'hugh', hash: HASH_1, savedAt: '2026-10-21T08:30:00Z', profile: profileFixture() },
    puts: [],
  }
  server.use(
    http.get('/api/profile', () => HttpResponse.json(api.current)),
    http.put('/api/profile', async ({ request }) => {
      const body = (await request.json()) as ProfileApi['puts'][number]
      api.puts.push(body)
      if (api.failNext) {
        const { status, body: problem } = api.failNext
        api.failNext = undefined
        return HttpResponse.json({ status, ...problem }, { status })
      }
      api.current = { id: 'hugh', hash: HASH_2, savedAt: '2026-10-22T09:00:00Z', profile: body.profile }
      return HttpResponse.json({
        id: 'hugh',
        hash: HASH_2,
        savedAt: '2026-10-22T09:00:00Z',
        reassessed: { jobs: 30, prefilterChanged: 2, checksUpdated: 13 },
      })
    }),
  )
  return api
}
