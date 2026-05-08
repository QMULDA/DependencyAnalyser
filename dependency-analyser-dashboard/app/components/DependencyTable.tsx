'use client';
import { useState, useMemo } from 'react';
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  flexRender,
  type ColumnDef,
  type SortingState,
  type PaginationState,
} from '@tanstack/react-table';
import { DetailRow, TIER_COLORS } from '../../lib/types';

function RiskPill({ tier }: { tier: string | null }) {
  if (!tier) return <span className="text-gray-500">—</span>;
  const color = TIER_COLORS[tier] ?? '#6b7280';
  const bgMap: Record<string, string> = {
    NONE: 'bg-gray-700 text-gray-200',
    LOW: 'bg-green-900/60 text-green-300',
    MEDIUM: 'bg-amber-900/60 text-amber-300',
    HIGH: 'bg-red-900/60 text-red-300',
  };
  void color;
  return (
    <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${bgMap[tier] ?? 'bg-gray-700 text-gray-300'}`}>
      {tier}
    </span>
  );
}

const columns: ColumnDef<DetailRow>[] = [
  {
    accessorKey: 'project_name',
    header: 'Project',
  },
  {
    id: 'library',
    accessorFn: row => `${row.group_id}:${row.artifact_id}`,
    header: 'Library',
    cell: ({ getValue }) => (
      <span className="font-mono text-xs">{getValue<string>()}</span>
    ),
  },
  {
    accessorKey: 'version_string',
    header: 'Version',
    cell: ({ getValue }) => (
      <span className="font-mono text-xs">{getValue<string>()}</span>
    ),
  },
  {
    accessorKey: 'risk_tier',
    header: 'Risk Tier',
    cell: ({ getValue }) => <RiskPill tier={getValue<string | null>()} />,
  },
  {
    accessorKey: 'relation',
    header: 'Type',
    cell: ({ getValue }) => getValue<string>() === 'DIRECT' ? 'Direct' : 'Indirect',
  },
  {
    accessorKey: 'cve_count',
    header: 'CVEs',
    cell: ({ getValue }) => {
      const n = getValue<number>();
      return n > 0
        ? <span className="text-red-400 font-medium">{n}</span>
        : <span className="text-gray-500">0</span>;
    },
  },
  {
    accessorKey: 'scanned_at',
    header: 'Last Scanned',
    cell: ({ getValue }) => getValue<string>().substring(0, 10),
  },
];

interface Props {
  data: DetailRow[];
}

export default function DependencyTable({ data }: Props) {
  const [sorting, setSorting] = useState<SortingState>([]);
  const [globalFilter, setGlobalFilter] = useState('');
  const [pagination, setPagination] = useState<PaginationState>({ pageIndex: 0, pageSize: 10 });

  const stableData = useMemo(() => data, [data]);

  const table = useReactTable({
    data: stableData,
    columns,
    state: { sorting, globalFilter, pagination },
    onSortingChange: setSorting,
    onGlobalFilterChange: setGlobalFilter,
    onPaginationChange: setPagination,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
  });

  const { pageIndex, pageSize } = table.getState().pagination;
  const pageCount = table.getPageCount();

  return (
    <div className="space-y-3">
      {/* Search */}
      <input
        type="text"
        value={globalFilter}
        onChange={e => {
          setGlobalFilter(e.target.value);
          setPagination(p => ({ ...p, pageIndex: 0 }));
        }}
        placeholder="Search dependencies…"
        className="w-full max-w-sm bg-gray-800 border border-gray-600 rounded px-3 py-1.5 text-sm text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
      />

      {/* Table */}
      <div className="overflow-x-auto rounded-lg border border-gray-800">
        <table className="w-full text-sm text-left border-collapse">
          <thead className="bg-gray-800/60">
            <tr>
              {table.getFlatHeaders().map(header => (
                <th
                  key={header.id}
                  className={`px-3 py-2.5 text-xs font-medium text-gray-400 whitespace-nowrap select-none ${header.column.getCanSort() ? 'cursor-pointer hover:text-gray-200' : ''}`}
                  onClick={header.column.getToggleSortingHandler()}
                >
                  <span className="flex items-center gap-1">
                    {flexRender(header.column.columnDef.header, header.getContext())}
                    {header.column.getIsSorted() === 'asc'  && ' ↑'}
                    {header.column.getIsSorted() === 'desc' && ' ↓'}
                    {header.column.getIsSorted() === false  && header.column.getCanSort() && (
                      <span className="text-gray-600"> ↕</span>
                    )}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {table.getRowModel().rows.length === 0 ? (
              <tr>
                <td
                  colSpan={columns.length}
                  className="px-3 py-8 text-center text-gray-500 text-sm"
                >
                  No dependencies match the current filters.
                </td>
              </tr>
            ) : (
              table.getRowModel().rows.map(row => (
                <tr
                  key={row.id}
                  className="border-t border-gray-800 hover:bg-gray-800/40 transition-colors"
                >
                  {row.getVisibleCells().map(cell => (
                    <td key={cell.id} className="px-3 py-2 text-gray-300 whitespace-nowrap">
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-gray-400">
        <div className="flex items-center gap-2">
          <span>Rows per page:</span>
          <select
            value={pageSize}
            onChange={e => {
              table.setPageSize(Number(e.target.value));
              setPagination(p => ({ ...p, pageIndex: 0 }));
            }}
            className="bg-gray-800 border border-gray-600 rounded px-2 py-0.5 text-gray-100 focus:outline-none"
          >
            {[10, 25, 50].map(n => (
              <option key={n} value={n}>{n}</option>
            ))}
          </select>
        </div>
        <span className="text-gray-500 text-xs">
          {table.getFilteredRowModel().rows.length} total row{table.getFilteredRowModel().rows.length !== 1 ? 's' : ''}
        </span>
        <div className="flex items-center gap-2">
          <button
            onClick={() => table.previousPage()}
            disabled={!table.getCanPreviousPage()}
            className="px-2.5 py-1 rounded bg-gray-800 border border-gray-700 disabled:opacity-40 hover:bg-gray-700 transition-colors"
          >
            ‹ Prev
          </button>
          <span className="text-xs">
            Page {pageCount === 0 ? 0 : pageIndex + 1} of {pageCount}
          </span>
          <button
            onClick={() => table.nextPage()}
            disabled={!table.getCanNextPage()}
            className="px-2.5 py-1 rounded bg-gray-800 border border-gray-700 disabled:opacity-40 hover:bg-gray-700 transition-colors"
          >
            Next ›
          </button>
        </div>
      </div>
    </div>
  );
}
