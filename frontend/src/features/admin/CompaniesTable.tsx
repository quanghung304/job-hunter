import { type Company, useCompanies } from '@/api/queries'
import { ErrorState } from '@/components/ErrorState'
import { ListSkeleton } from '@/components/ListSkeleton'
import { SectionCard } from '@/components/SectionCard'
import { dateTime, relativeTime } from '@/lib/format'
import { AdminTag } from './AdminTag'

const ATS: Record<Company['ats'], string> = {
  GREENHOUSE: 'Greenhouse',
  LEVER: 'Lever',
  LEVER_EU: 'Lever (EU)',
  ASHBY: 'Ashby',
}

function checkTag(company: Company) {
  if (!company.enabled) return <AdminTag tone="neutral">Off</AdminTag>
  if (company.lastCheckStatus === 'OK') return <AdminTag tone="ok">OK</AdminTag>
  if (company.lastCheckStatus === 'NOT_FOUND') return <AdminTag tone="attention">Not found</AdminTag>
  if (company.lastCheckStatus === 'ERROR') return <AdminTag tone="strong">Error</AdminTag>
  return <AdminTag tone="neutral">Not checked</AdminTag>
}

const TH = 'border-b border-line px-3 py-2 text-left text-[12px] font-semibold tracking-[0.04em] text-muted uppercase'
const TD = 'border-b border-line-faint px-3 py-2.5 align-middle whitespace-nowrap'

/** Target companies with their ATS board and last check (edited in config/companies.yaml). */
export function CompaniesTable() {
  const companies = useCompanies()
  return (
    <SectionCard
      label="Companies"
      actions={<span className="num text-[12.5px] text-muted">Edit list in config/companies.yaml</span>}
    >
      {companies.isPending ? (
        <ListSkeleton rows={3} />
      ) : companies.isError ? (
        <ErrorState error={companies.error} title="Could not load the companies" />
      ) : companies.data.length === 0 ? (
        <p className="m-0 text-[13.5px] text-muted">No companies yet. Add them in config/companies.yaml.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-[13.5px]">
            <thead>
              <tr>
                <th className={TH}>Company</th>
                <th className={TH}>ATS</th>
                <th className={TH}>Board slug</th>
                <th className={TH}>Check</th>
                <th className={`${TH} text-right`}>Active jobs</th>
                <th className={TH}>Checked</th>
              </tr>
            </thead>
            <tbody>
              {companies.data.map((company) => (
                <tr key={company.id}>
                  <td className={`${TD} font-medium text-ink`}>{company.name}</td>
                  <td className={TD}>{ATS[company.ats]}</td>
                  <td className={`${TD} num text-[12.5px]`}>{company.slug}</td>
                  <td className={TD}>{checkTag(company)}</td>
                  <td className={`${TD} num text-right`}>{company.activeJobs}</td>
                  <td className={`${TD} num text-[12.5px] text-muted`}>
                    {company.lastCheckedAt ? (
                      <time dateTime={company.lastCheckedAt} title={dateTime(company.lastCheckedAt)}>
                        {relativeTime(company.lastCheckedAt)}
                      </time>
                    ) : (
                      '—'
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </SectionCard>
  )
}
