import type { Repairer, ScanResult } from '../src/types';

export const REPAIRERS: Repairer[] = [
  {
    id: 'FieldsInvalidEnumerationValueRepairer',
    name: 'Invalid enumeration value',
    description: 'Fixes invalid enumeration values.',
    configs: [
      {
        key: 'removeInvalid',
        description: 'Remove invalid values',
        hint: 'Removes values that no longer exist',
        type: 'BOOLEAN',
        defaultValue: true,
      },
    ],
  },
  {
    id: 'ModuleStandardStructureLinkRoleRepairer',
    name: 'Module structure link role',
    description: 'Fixes module structure link roles.',
    configs: [],
  },
];

// A larger repairer set for the "Repairers expanded" visual snapshot: five repairers with 0-3 boolean
// settings each and mixed default values, so the shot shows some settings ticked and some not. Default
// selection checks all but the opt-out ModuleStandardStructureLinkRoleRepairer (four of five checked);
// that unchecked card shows only its name/description, since settings render only under a checked repairer.
export const REPAIRERS_MANY: Repairer[] = [
  {
    id: 'FieldsInvalidEnumerationValueRepairer',
    name: 'Invalid enumeration value',
    description: 'Fixes invalid enumeration values.',
    configs: [
      {
        key: 'removeInvalid',
        description: 'Remove invalid values',
        hint: 'Drop values that no longer exist',
        type: 'BOOLEAN',
        defaultValue: true,
      },
      {
        key: 'keepHistory',
        description: 'Keep a history note',
        hint: 'Record the removed value in the history',
        type: 'BOOLEAN',
        defaultValue: false,
      },
      {
        key: 'notifyOwner',
        description: 'Notify the work item owner',
        hint: 'Send a notification after repair',
        type: 'BOOLEAN',
        defaultValue: true,
      },
    ],
  },
  {
    id: 'FieldsRequiredFieldsRepairer',
    name: 'Required fields',
    description: 'Populates missing required fields.',
    configs: [
      {
        key: 'fillDefaults',
        description: 'Fill with default values',
        hint: 'Use each field default when empty',
        type: 'BOOLEAN',
        defaultValue: true,
      },
      {
        key: 'skipReadonly',
        description: 'Skip read-only fields',
        hint: 'Leave read-only fields untouched',
        type: 'BOOLEAN',
        defaultValue: false,
      },
    ],
  },
  {
    id: 'LinksBrokenTargetRepairer',
    name: 'Broken link targets',
    description: 'Removes links pointing at missing targets.',
    configs: [
      {
        key: 'removeBroken',
        description: 'Remove broken links',
        hint: 'Delete links whose target is gone',
        type: 'BOOLEAN',
        defaultValue: true,
      },
      {
        key: 'reportCrossProject',
        description:
          'Also report links whose target still exists but points outside the current project scope, which usually indicates a misconfigured module import and is left untouched by default',
        hint: 'Cross-project links are only reported, never removed automatically',
        type: 'BOOLEAN',
        defaultValue: false,
      },
    ],
  },
  {
    id: 'ModuleStandardStructureLinkRoleRepairer',
    name: 'Module structure link role',
    description: 'Fixes module structure link roles.',
    configs: [],
  },
  {
    id: 'WorkItemOrphanedRepairer',
    name: 'Orphaned work items',
    description: 'Re-attaches work items with no parent.',
    configs: [
      {
        key: 'attachToRoot',
        description: 'Attach to document root',
        hint: 'Move orphans under the root node',
        type: 'BOOLEAN',
        defaultValue: true,
      },
      {
        key: 'dryRun',
        description: 'Report only (no changes)',
        hint: 'List orphans without moving them',
        type: 'BOOLEAN',
        defaultValue: false,
      },
    ],
  },
];

export const WORK_ITEM_TYPES = [
  { id: 'requirement', name: 'Requirement', iconURL: '/polarion/icons/req.svg' },
  { id: 'task', name: 'Task', iconURL: '/polarion/icons/task.svg' },
];

export const DOCUMENT_TYPES = [{ id: 'generic', name: 'Generic', iconURL: null }];

export const BASELINES = [
  { revision: '4321', name: 'Release 1.0' },
  { revision: '0', name: 'invalid' },
];

// A scan result exercising: a repairable work item with two issues + a warning, a work item pinned to
// a revision (non-repairable), and a collection with two sub-items that have issues.
export const SCAN_RESULT: ScanResult = {
  report: 'Scanned 4 entities in 1.2s',
  items: [
    {
      entityType: 'WORKITEM',
      projectId: 'elibrary',
      space: null,
      entityId: 'EL-100',
      revision: null,
      issues: [
        {
          metaInfo: 'meta-1',
          repairer: 'FieldsInvalidEnumerationValueRepairer',
          description: 'Bad enum in EL-100',
          warnings: [],
        },
        {
          metaInfo: 'meta-2',
          repairer: 'ModuleStandardStructureLinkRoleRepairer',
          description: 'Bad link role in EL-100',
          warnings: [],
        },
      ],
      fields: {},
      subitems: [],
      warnings: ['Heads up: EL-100 has a soft warning'],
    },
    {
      entityType: 'WORKITEM',
      projectId: 'elibrary',
      space: null,
      entityId: 'EL-200',
      revision: '4321',
      issues: [
        {
          metaInfo: 'meta-3',
          repairer: 'FieldsInvalidEnumerationValueRepairer',
          description: 'Bad enum in EL-200',
          warnings: [],
        },
      ],
      fields: {},
      subitems: [],
      warnings: [],
    },
    {
      entityType: 'COLLECTION',
      projectId: 'elibrary',
      space: 'coll',
      entityId: 'COLL-1',
      revision: null,
      issues: [],
      fields: {},
      warnings: [],
      subitems: [
        {
          entityType: 'DOCUMENT',
          projectId: 'elibrary',
          space: 'spaceA',
          entityId: 'DOC-1',
          revision: null,
          issues: [
            {
              metaInfo: 'meta-4',
              repairer: 'FieldsInvalidEnumerationValueRepairer',
              description: 'Bad enum in DOC-1',
              warnings: [],
            },
          ],
          fields: {},
          subitems: [],
          warnings: [],
        },
        {
          entityType: 'DOCUMENT',
          projectId: 'elibrary',
          space: 'spaceA',
          entityId: 'DOC-2',
          revision: null,
          issues: [
            {
              metaInfo: 'meta-5',
              repairer: 'ModuleStandardStructureLinkRoleRepairer',
              description: 'Bad link role in DOC-2',
              warnings: [],
            },
          ],
          fields: {},
          subitems: [],
          warnings: [],
        },
      ],
    },
  ],
};
