/*
 *  Copyright (C) 2005-2015 Alfresco Software Limited.
 *
 *  This file is part of Alfresco Mobile for Android.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.alfresco.mobile.android.application.fragments.workflow.task;

import java.io.Serializable;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.*;

import org.alfresco.mobile.android.api.constants.OnPremiseConstant;
import org.alfresco.mobile.android.api.constants.WorkflowModel;
import org.alfresco.mobile.android.api.model.Document;
import org.alfresco.mobile.android.api.model.Node;
import org.alfresco.mobile.android.api.model.Person;
import org.alfresco.mobile.android.api.model.Process;
import org.alfresco.mobile.android.api.model.Task;
import org.alfresco.mobile.android.api.model.impl.ProcessImpl;
import org.alfresco.mobile.android.api.model.impl.TaskImpl;
import org.alfresco.mobile.android.api.services.impl.publicapi.PublicAPIWorkflowServiceImpl;
import org.alfresco.mobile.android.api.utils.WorkflowUtils;
import org.alfresco.mobile.android.application.R;
import org.alfresco.mobile.android.application.fragments.DisplayUtils;
import org.alfresco.mobile.android.application.fragments.FragmentDisplayer;
import org.alfresco.mobile.android.application.fragments.MenuFragmentHelper;
import org.alfresco.mobile.android.application.fragments.builder.LeafFragmentBuilder;
import org.alfresco.mobile.android.application.fragments.node.details.NodeDetailsFragment;
import org.alfresco.mobile.android.application.fragments.user.UserPickerCallback;
import org.alfresco.mobile.android.application.fragments.user.UserProfileFragment;
import org.alfresco.mobile.android.application.fragments.user.UserSearchFragment;
import org.alfresco.mobile.android.application.fragments.workflow.ProcessDiagramFragment;
import org.alfresco.mobile.android.application.fragments.workflow.process.ProcessTasksFragment;
import org.alfresco.mobile.android.application.fragments.workflow.process.ProcessesAdapter;
import org.alfresco.mobile.android.async.Operator;
import org.alfresco.mobile.android.async.workflow.ItemsEvent;
import org.alfresco.mobile.android.async.workflow.ItemsRequest;
import org.alfresco.mobile.android.async.workflow.process.start.StartProcessRequest;
import org.alfresco.mobile.android.async.workflow.task.complete.CompleteTaskEvent;
import org.alfresco.mobile.android.async.workflow.task.complete.CompleteTaskRequest;
import org.alfresco.mobile.android.async.workflow.task.delegate.ReassignTaskEvent;
import org.alfresco.mobile.android.async.workflow.task.delegate.ReassignTaskRequest;
import org.alfresco.mobile.android.platform.exception.CloudExceptionUtils;
import org.alfresco.mobile.android.platform.extensions.AnalyticsManager;
import org.alfresco.mobile.android.platform.mimetype.MimeTypeManager;
import org.alfresco.mobile.android.platform.utils.SessionUtils;
import org.alfresco.mobile.android.ui.ListingModeFragment;
import org.alfresco.mobile.android.ui.fragments.AlfrescoFragment;
import org.alfresco.mobile.android.ui.operation.OperationWaitingDialogFragment;
import org.alfresco.mobile.android.ui.rendition.RenditionManager;
import org.alfresco.mobile.android.ui.utils.Formatter;
import org.alfresco.mobile.android.ui.utils.UIUtils;
import org.alfresco.mobile.android.ui.workflow.task.TasksFoundationAdapter;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.text.Html;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import java.util.Iterator;

import com.squareup.otto.Subscribe;

/**
 * @since 1.3
 * @author Jean Marie Pascal
 */
public class TaskDetailsFragment extends AlfrescoFragment implements UserPickerCallback, AdapterView.OnItemSelectedListener
{

    public static final String TAG = TaskDetailsFragment.class.getName();

    private static final String ARGUMENT_TASK = "paramTask";

    private static final String ARGUMENT_PROCESS = "TaskProcess";

    private View vRoot;

    private Task currentTask;

    private Process currentProcess;

    private EditText comment;

    private boolean isReviewTask = false;

    private Person initiator;

    private List<Document> items;

    private List<OutcomeChoice> AvailableOutcomes = new ArrayList<OutcomeChoice>();

    // ///////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS & HELPERS
    // ///////////////////////////////////////////////////////////////////////////
    public TaskDetailsFragment()
    {
        setHasOptionsMenu(true);
        screenName = AnalyticsManager.SCREEN_TASK_DETAILS;
    }

    protected static TaskDetailsFragment newInstanceByTemplate(Bundle b)
    {
        TaskDetailsFragment bf = new TaskDetailsFragment();
        bf.setArguments(b);
        return bf;
    }

    // ///////////////////////////////////////////////////////////////////////////
    // LIFECYCLE
    // ///////////////////////////////////////////////////////////////////////////
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        setRetainInstance(true);

        container.setVisibility(View.VISIBLE);
        setSession(SessionUtils.getSession(getActivity()));
        SessionUtils.checkSession(getActivity(), getSession());
        vRoot = inflater.inflate(R.layout.app_task_details, container, false);

        if (getSession() == null) { return vRoot; }

        currentTask = (Task) getArguments().get(ARGUMENT_TASK);
        currentProcess = (Process) getArguments().get(ARGUMENT_PROCESS);
        if (currentTask == null && currentProcess == null) { return null; }

        // Init variable depending on object
        initVariables();

        // Header
        TextView tv = (TextView) vRoot.findViewById(R.id.title);
        tv.setText(description);

        // Other parts
        initHeader();
        initCompleteForm();
        initInitiator();

        if (items == null)
        {
            Operator.with(getActivity(), SessionUtils.getAccount(getActivity())).load(
                    new ItemsRequest.Builder(currentProcess, currentTask));
        }
        else
        {
            diplayAttachment();
        }

        return vRoot;
    }

    @Override
    public void onResume()
    {
        super.onResume();
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (!DisplayUtils.hasCentralPane(getActivity()))
        {
            UIUtils.displayTitle(getActivity(), R.string.details);
        }
    }

    // ///////////////////////////////////////////////////////////////////////////
    // INIT
    // ///////////////////////////////////////////////////////////////////////////
    private String state = null;

    private String description;

    private String type;

    private int priority;

    private GregorianCalendar endedAt;

    private GregorianCalendar dueAt;

    private String processId;

    private String processDefinitionId;

    private ArrayList<String> RejectReason = new ArrayList<String>();

    private void initVariables()
    {
        if (currentTask != null)
        {
            description = currentTask.getDescription();
            priority = currentTask.getPriority();
            endedAt = currentTask.getEndedAt();
            if (((TaskImpl) currentTask).getData().containsKey(OnPremiseConstant.WORKFLOWINSTANCE_VALUE))
            {
                Process p = (Process) ((TaskImpl) currentTask).getData().get(OnPremiseConstant.WORKFLOWINSTANCE_VALUE);
                initiator = (Person) ((ProcessImpl) p).getData().get(OnPremiseConstant.INITIATOR_VALUE);
            }
            type = currentTask.getName();
            dueAt = currentTask.getDueAt();
            processId = currentTask.getProcessIdentifier();
            processDefinitionId = currentTask.getProcessDefinitionIdentifier();
            state = (String) ((TaskImpl) currentTask).getData().get(OnPremiseConstant.STATE_VALUE);
        }
        else if (currentProcess != null)
        {
            description = currentProcess.getDescription() != null ? currentProcess.getDescription()
                    : getString(R.string.process_no_description);
            priority = currentProcess.getPriority();
            endedAt = currentProcess.getEndedAt();
            initiator = (Person) ((ProcessImpl) currentProcess).getData().get(OnPremiseConstant.INITIATOR_VALUE);
            type = ProcessesAdapter.getName(getActivity(), currentProcess.getKey());
            dueAt = ((ProcessImpl) currentProcess).getDueAt();
            processId = currentProcess.getIdentifier();
            processDefinitionId = currentProcess.getDefinitionIdentifier();
        }
    }

    public void initCompleteForm()
    {
        RejectReason.add("--- Sélectionner un motif en cas de rejet ---"); //TODO : Use localisation files

        if (currentProcess != null)
        {
            vRoot.findViewById(R.id.complete_group).setVisibility(View.GONE);
            return;
        }

        if (currentTask != null && endedAt == null)
        {
            View validation = vRoot.findViewById(R.id.action_approve);

            //View reject = vRoot.findViewById(R.id.action_reject);

            comment = (EditText) vRoot.findViewById(R.id.task_comment);

            if (currentTask.getKey().startsWith("supplierinvoice"))
            {
                switch(currentTask.getKey().substring(currentTask.getKey().indexOf(':') +1 ))
                {
                    case "buyerApprovalLevel1Task":
                        AvailableOutcomes.add(new OutcomeChoice("Approve", currentTask.getKey()+"Outcome", "Approve", false));
                        AvailableOutcomes.add(new OutcomeChoice("Litigation", currentTask.getKey()+"Outcome", "Litigation", false));
                        AvailableOutcomes.add(new OutcomeChoice("Resend To LAD", currentTask.getKey()+"Outcome", "ResendToLAD", false));
                        AvailableOutcomes.add(new OutcomeChoice("Reject", currentTask.getKey()+"Outcome", "Reject" , true));
                        break;
                    case "buyerApprovalLevel2Task":
                        AvailableOutcomes.add(new OutcomeChoice("Approve", currentTask.getKey()+"Outcome", "Approve", false));
                        AvailableOutcomes.add(new OutcomeChoice("Reject", currentTask.getKey()+"Outcome", "Reject" , true));
                        break;
                    case "litigationHandlingTask":
                        AvailableOutcomes.add(new OutcomeChoice("Resubmit", currentTask.getKey()+"Outcome", "Resubmit", false));
                        AvailableOutcomes.add(new OutcomeChoice("Reject", currentTask.getKey()+"Outcome", "Reject" , true));
                        break;
                    case "controllingApprovalTask":
                        AvailableOutcomes.add(new OutcomeChoice("Approve", currentTask.getKey()+"Outcome", "Approve", false));
                        AvailableOutcomes.add(new OutcomeChoice("Reject", currentTask.getKey()+"Outcome", "Reject" , true));
                        break;
                    case "accountingApprovalTask":
                        AvailableOutcomes.add(new OutcomeChoice("Approve", currentTask.getKey()+"Outcome", "Approve", false));
                        AvailableOutcomes.add(new OutcomeChoice("Reject", currentTask.getKey()+"Outcome", "Reject" , true));
                        break;
                    case "paymentTask":
                        AvailableOutcomes.add(new OutcomeChoice("Payed", currentTask.getKey()+"Outcome", "Payed", false));
                        break;
                    case "rollbackAccountingTask":
                        AvailableOutcomes.add(new OutcomeChoice("Done", currentTask.getKey()+"Outcome", "Done", false));
                        break;
                    default:
                        AvailableOutcomes.add(new OutcomeChoice("Approve", currentTask.getKey()+"Outcome", "Approve", false));
                        AvailableOutcomes.add(new OutcomeChoice("Reject", currentTask.getKey()+"Outcome", "Reject" , true));
                        break;
                }

                //TODO : Use localisation files
                RejectReason.add("Ne nous concerne pas");
                RejectReason.add("Marchandises ou prestations facturés non conformes");
                RejectReason.add("Marchandises ou prestations déjà facturées");
                RejectReason.add("Aucune sélection");
            }
            else if (  WorkflowModel.TASK_REVIEW.equals(currentTask.getKey())
                    || WorkflowModel.TASK_ACTIVITI_REVIEW.equals(currentTask.getKey())
                    || "imwf:activitiModeratedInvitationReviewTask".equals(currentTask.getKey())
                    || "imwf:moderatedInvitationReviewTask".equals(currentTask.getKey()))
            {
                if ("imwf:activitiModeratedInvitationReviewTask".equals(currentTask.getKey()))
                {
                    AvailableOutcomes.add(new OutcomeChoice(WorkflowModel.TRANSITION_APPROVE, "imwf_reviewOutcome", WorkflowModel.TRANSITION_APPROVE.toLowerCase(), false));
                    AvailableOutcomes.add(new OutcomeChoice(WorkflowModel.TRANSITION_REJECT, "imwf_reviewOutcome", WorkflowModel.TRANSITION_REJECT.toLowerCase(),false));
                }
                else if ((getSession().getRepositoryInfo().getMajorVersion() < OnPremiseConstant.ALFRESCO_VERSION_4))
                {
                    AvailableOutcomes.add(new OutcomeChoice(WorkflowModel.TRANSITION_APPROVE, WorkflowModel.PROP_TRANSITIONS_VALUE, WorkflowModel.TRANSITION_APPROVE.toLowerCase(),false));
                    AvailableOutcomes.add(new OutcomeChoice(WorkflowModel.TRANSITION_REJECT, WorkflowModel.PROP_TRANSITIONS_VALUE, WorkflowModel.TRANSITION_REJECT.toLowerCase(),false));
                }
                else
                {
                    AvailableOutcomes.add(new OutcomeChoice(WorkflowModel.TRANSITION_APPROVE, WorkflowModel.PROP_REVIEW_OUTCOME, WorkflowModel.TRANSITION_APPROVE.toLowerCase(),false));
                    AvailableOutcomes.add(new OutcomeChoice(WorkflowModel.TRANSITION_REJECT, WorkflowModel.PROP_REVIEW_OUTCOME, WorkflowModel.TRANSITION_REJECT.toLowerCase(),false));
                }

                //isReviewTask = true;
                //reject.setVisibility(View.VISIBLE);
            }
            else
            {
                /*
                reject.setVisibility(View.GONE);
                if (validation instanceof Button)
                {
                    ((Button) validation).setText(R.string.done);
                }
                */
                AvailableOutcomes.add(new OutcomeChoice("Done", "", "",false));;
            }

            ArrayList<String> list = new ArrayList<String>();

            for(int i=0; i<AvailableOutcomes.size(); i++)
            {
                list.add(AvailableOutcomes.get(i).DisplayName);
            }
            /*
            Iterator<String> itr = AvailableOutcomes.keySet().iterator();
            while (itr.hasNext()) {
                String Key = itr.next();
                list.add(AvailableOutcomes.get(Key).DisplayName);
            };
            */

            Collections.sort(list);

            Spinner ActionSelection = (Spinner)vRoot.findViewById(R.id.action_selection);
            ActionSelection.setOnItemSelectedListener(this);
            ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this.getContext(), android.R.layout.simple_spinner_item, list);
            dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            ActionSelection.setAdapter(dataAdapter);

            Spinner ReasonSelection = (Spinner)vRoot.findViewById(R.id.reason_selection);
            ArrayAdapter<String> dataAdapter2 = new ArrayAdapter<String>(this.getContext(), android.R.layout.simple_spinner_item, RejectReason);
            dataAdapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            ReasonSelection.setAdapter(dataAdapter2);
            ReasonSelection.setSelection(0);

            validation.setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                completeTask(currentTask, isReviewTask, true);
                }
            });

            /*
            reject.setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    completeTask(currentTask, isReviewTask, false);
                }
            });
            */
        }
        else
        {
            vRoot.findViewById(R.id.complete_group).setVisibility(View.GONE);
        }
    }

    private void initInitiator()
    {
        // Display Initiator
        if (initiator != null)
        {
            LinearLayout layout = (LinearLayout) vRoot.findViewById(R.id.task_initiator_group);
            layout.setOnClickListener(new OnClickListener()
            {
                public void onClick(View v)
                {
                    UserProfileFragment.with(getActivity()).personId(initiator.getIdentifier()).displayAsDialog();
                }
            });

            TextView tv = (TextView) vRoot.findViewById(R.id.task_initiator);
            tv.setText(initiator.getFullName());
        }
        else
        {
            vRoot.findViewById(R.id.task_initiator_group).setVisibility(View.GONE);
            vRoot.findViewById(R.id.task_initiator_icon).setVisibility(View.GONE);
        }
    }

    private void initHeader()
    {
        // PRIORITY
        ImageView icon = (ImageView) vRoot.findViewById(R.id.task_priority_icon);
        TextView textValue = (TextView) vRoot.findViewById(R.id.task_priority);

        icon.setImageDrawable(getResources().getDrawable(TasksFoundationAdapter.getPriorityIconId(priority)));
        int labelId = R.string.workflow_priority_medium;
        switch (priority)
        {
            case WorkflowModel.PRIORITY_HIGH:
                labelId = R.string.workflow_priority_high;
                break;
            case WorkflowModel.PRIORITY_MEDIUM:
                labelId = R.string.workflow_priority_medium;
                break;
            case WorkflowModel.PRIORITY_LOW:
                labelId = R.string.workflow_priority_low;
                break;
            default:
                break;
        }
        textValue.setText(labelId);

        // TASK TYPE
        textValue = (TextView) vRoot.findViewById(R.id.task_type);
        textValue.setText(type);

        // DUE DATE
        StringBuilder builder = new StringBuilder();
        if (dueAt != null)
        {
            textValue = (TextView) vRoot.findViewById(R.id.task_due_date);
            if (dueAt.before(new GregorianCalendar()))
            {
                builder.append("<b>");
                builder.append("<font color='#9F000F'>");
                builder.append(DateFormat.getLongDateFormat(getActivity()).format(dueAt.getTime()));
                builder.append("</font>");
                builder.append("</b>");
            }
            else
            {
                builder.append(DateFormat.getLongDateFormat(getActivity()).format(dueAt.getTime()));
            }
            textValue.setText(builder.toString());
            textValue.setText(Html.fromHtml(builder.toString()), TextView.BufferType.SPANNABLE);
        }
        else
        {
            vRoot.findViewById(R.id.task_due_date_group).setVisibility(View.GONE);
            vRoot.findViewById(R.id.task_due_date_icon).setVisibility(View.GONE);
        }
    }

    private void diplayAttachment()
    {
        vRoot.findViewById(R.id.attachments_waiting).setVisibility(View.GONE);
        LinearLayout ll = (LinearLayout) vRoot.findViewById(R.id.attachments);
        if (items == null || items.isEmpty())
        {
            ImageView iv = new ImageView(getActivity());
            iv.setScaleType(ScaleType.FIT_CENTER);
            iv.setImageResource(R.drawable.mime_empty_doc);
            ll.addView(iv, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

            TextView tv = new TextView(getActivity());
            tv.setText(R.string.process_no_attachments);
            tv.setGravity(Gravity.CENTER);
            ll.addView(tv, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            ll.setGravity(Gravity.CENTER);
            return;
        }

        LayoutInflater li = LayoutInflater.from(getActivity());
        View vr = null;
        TextView tv = null;
        for (Node node : items)
        {
            vr = li.inflate(R.layout.app_task_item_row, ll, false);
            tv = (TextView) vr.findViewById(R.id.toptext);
            tv.setText(node.getName());
            tv = (TextView) vr.findViewById(R.id.bottomtext);
            tv.setText(createContentBottomText(getActivity(), node));
            ll.addView(vr, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            ImageView iv = (ImageView) vr.findViewById(R.id.icon);
            RenditionManager.with(getActivity()).loadNode(node)
                    .placeHolder(MimeTypeManager.getInstance(getActivity()).getIcon(node.getName(), true)).into(iv);
            vr.setTag(node);
            vr.setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    Node item = (Node) v.getTag();
                    NodeDetailsFragment.with(getActivity()).nodeId(item.getIdentifier()).back(true).display();
                }
            });
        }
    }

    private String createContentBottomText(Context context, Node node)
    {
        String s = "";

        if (node.getModifiedAt() != null)
        {
            s = Formatter.formatToRelativeDate(context, node.getModifiedAt().getTime());
            if (node.isDocument())
            {
                Document doc = (Document) node;
                s += " - " + Formatter.formatFileSize(context, doc.getContentStreamLength());
            }
        }
        return s;
    }

    // ///////////////////////////////////////////////////////////////////////////
    // INTERNALS
    // ///////////////////////////////////////////////////////////////////////////
    private void completeTask(Task task, boolean isReviewTask, boolean isApprove)
    {
        // Prepare Variables
        Map<String, Serializable> variables = new HashMap<String, Serializable>(3);

        Spinner  ActionSelection = (Spinner)vRoot.findViewById(R.id.action_selection);
        if (ActionSelection.getSelectedItem() == null) return; //No choice was made (means that no options were available for this type of task)
        String SelectedItem = ActionSelection.getSelectedItem().toString();

        OutcomeChoice OC = null;
        for(int i=0;i<AvailableOutcomes.size(); i++) {
            if (AvailableOutcomes.get(i).DisplayName.equals(SelectedItem)) {
                OC = (OutcomeChoice) AvailableOutcomes.get(i);
                break;
            }
        }

        Spinner ReasonSelection = (Spinner)vRoot.findViewById(R.id.reason_selection);
        if (OC.RequiresReason)
        {
            if (ReasonSelection.getSelectedItemPosition() == 0)
            {
                ReasonSelection.requestFocus();
                return;
            }
            else
            {
                String RejectionReasonKey = currentTask.getKey().substring(0, currentTask.getKey().indexOf(':')) + "_rejectionReason";
                variables.put(RejectionReasonKey,ReasonSelection.getSelectedItem().toString());
            }
        }

        if (OC.OutcomePropertyName != "")
        {
            variables.put(OC.OutcomePropertyName,OC.OutcomeValue);
        }
/*
        if (isReviewTask)
        {
            String outcome = (isApprove) ? WorkflowModel.TRANSITION_APPROVE : WorkflowModel.TRANSITION_REJECT;
            if (!(getSession().getServiceRegistry().getWorkflowService() instanceof PublicAPIWorkflowServiceImpl))
            {
                outcome = (task.getProcessDefinitionIdentifier().startsWith(WorkflowModel.KEY_PREFIX_ACTIVITI)) ? outcome
                        : outcome.toLowerCase();
            }

            // TODO Move constants to SDK level
            if ("imwf:activitiModeratedInvitationReviewTask".equals(task.getKey()))
            {
                outcome = outcome.toLowerCase();
                variables.put("imwf_reviewOutcome", outcome);
            }
            else if ((getSession().getRepositoryInfo().getMajorVersion() < OnPremiseConstant.ALFRESCO_VERSION_4))
            {
                variables.put(WorkflowModel.PROP_TRANSITIONS_VALUE, outcome);
            }
            else
            {
                variables.put(WorkflowModel.PROP_REVIEW_OUTCOME, outcome);
            }
        }
*/
        if (comment.getText().length() > 0)
        {
            variables.put(WorkflowModel.PROP_COMMENT, comment.getText().toString());
        }

        String operationId = Operator.with(getActivity(), getAccount()).load(new CompleteTaskRequest.Builder(task, variables));

        OperationWaitingDialogFragment.newInstance(CompleteTaskRequest.TYPE_ID, R.drawable.ic_validate,
                getString(R.string.task_completing), null, null, 0, operationId).show(
                getActivity().getSupportFragmentManager(), OperationWaitingDialogFragment.TAG);
    }

    // ///////////////////////////////////////////////////////////////////////////
    // MENU
    // ///////////////////////////////////////////////////////////////////////////
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        super.onCreateOptionsMenu(menu, inflater);
        if (!MenuFragmentHelper.canDisplayFragmentMenu(getActivity())) { return; }
        menu.clear();
        getMenu(menu);
    }

    public void getMenu(Menu menu)
    {
        MenuItem mi;

        String processDefinitionKey = WorkflowUtils.getKeyFromProcessDefinitionId(processDefinitionId);

        if (endedAt == null && processDefinitionKey.startsWith(WorkflowModel.KEY_PREFIX_ACTIVITI))
        {
            mi = menu.add(Menu.NONE, R.id.menu_process_details, Menu.FIRST, R.string.process_diagram);
            mi.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }

        mi = menu.add(Menu.NONE, R.id.menu_process_history, Menu.FIRST + 1, R.string.tasks_history);
        mi.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        if (currentTask == null || endedAt != null) { return; }

        // unclaim : I unassign myself (generally created by a pooled process)
        if (currentTask.getAssigneeIdentifier() != null
                && WorkflowModel.FAMILY_PROCESS_POOLED_REVIEW.contains(processDefinitionKey))
        {
            mi = menu.add(Menu.NONE, R.id.menu_task_unclaim, Menu.FIRST + 2, R.string.task_unclaim);
            mi.setShowAsAction(MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        }
        // reassign : I have a task and I decide I dont want to be responsible
        // anymore of this task so I reassign to a specific person
        else if (currentTask.getAssigneeIdentifier() != null)
        {
            mi = menu.add(Menu.NONE, R.id.menu_task_reassign, Menu.FIRST + 3, R.string.task_reassign);
            mi.setIcon(R.drawable.ic_reassign);
            mi.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        // claim : I assign to me an unassigned task (created by a pooled
        // process)
        else if ((currentTask.getAssigneeIdentifier() == null && WorkflowModel.FAMILY_PROCESS_POOLED_REVIEW
                .contains(processDefinitionKey)) || "unclaimed".equals(state))
        {
            mi = menu.add(Menu.NONE, R.id.menu_task_claim, Menu.FIRST + 4, R.string.task_claim);
            mi.setShowAsAction(MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.menu_task_reassign:
                reassign();
                return true;
            case R.id.menu_task_claim:
                claim();
                return true;
            case R.id.menu_process_history:
                displayHistory();
                return true;
            case R.id.menu_task_unclaim:
                unclaim();
                return true;
            case R.id.menu_process_details:
                showProcessDiagram();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ///////////////////////////////////////////////////////////////////////////
    // MENU METHODS
    // ///////////////////////////////////////////////////////////////////////////
    public void reassign()
    {
        UserSearchFragment.with(getActivity()).fragmentTag(TAG).singleChoice(true).mode(ListingModeFragment.MODE_PICK)
                .displayAsDialog();
    }

    public void claim()
    {
        // Start claim
        String operationId = Operator.with(getActivity(), getAccount()).load(
                new ReassignTaskRequest.Builder(currentTask, getSession().getPersonIdentifier(), true));

        OperationWaitingDialogFragment.newInstance(StartProcessRequest.TYPE_ID, R.drawable.ic_reassign,
                getString(R.string.task_reassign), null, null, 0, operationId).show(
                getActivity().getSupportFragmentManager(),
                OperationWaitingDialogFragment.TAG);
    }

    public void unclaim()
    {
        // Start unclaim
        String operationId = Operator.with(getActivity(), getAccount()).load(
                new ReassignTaskRequest.Builder(currentTask, getSession().getPersonIdentifier(), false));

        OperationWaitingDialogFragment.newInstance(StartProcessRequest.TYPE_ID, R.drawable.ic_reassign,
                getString(R.string.task_reassign), null, null, 0, operationId).show(
                getActivity().getSupportFragmentManager(),
                OperationWaitingDialogFragment.TAG);
    }

    public void showProcessDiagram()
    {
        AlfrescoFragment frag = ProcessDiagramFragment.newInstance(processId);
        frag.setSession(getSession());
        frag.show(getFragmentManager(), ProcessDiagramFragment.TAG);
    }

    public void displayHistory()
    {
        ProcessTasksFragment.with(getActivity()).processId(processId).displayAsDialog();
    }

    // ///////////////////////////////////////////////////////////////////////////
    // REASSIGN
    // ///////////////////////////////////////////////////////////////////////////
    @Override
    public void onPersonSelected(Map<String, Person> mapPerson)
    {
        Person delegatePerson = null;
        for (Entry<String, Person> assignee : mapPerson.entrySet())
        {
            delegatePerson = assignee.getValue();
            break;
        }

        // Start reassign
        String operationId = Operator.with(getActivity(), getAccount()).load(
                new ReassignTaskRequest.Builder(currentTask, delegatePerson));

        OperationWaitingDialogFragment.newInstance(StartProcessRequest.TYPE_ID, R.drawable.ic_reassign,
                getString(R.string.task_reassign), null, null, 0, operationId).show(
                getActivity().getSupportFragmentManager(),
                OperationWaitingDialogFragment.TAG);
    }

    @Override
    public Map<String, Person> retrieveSelection()
    {
        return new HashMap<String, Person>(1);
    }

    // ///////////////////////////////////////////////////////////////////////////
    // EVENTS RECEIVER
    // ///////////////////////////////////////////////////////////////////////////
    @Subscribe
    public void onAttachmentsRetrieved(ItemsEvent event)
    {
        if (event.hasException)
        {
            CloudExceptionUtils.handleCloudException(getActivity(), event.exception, false);
        }
        else
        {
            if (items != null)
            {
                items.clear();
            }
            items = event.data.getList();
            diplayAttachment();
        }
    }

    @Subscribe
    public void onTaskCompleted(CompleteTaskEvent event)
    {
        clearFragment();
    }

    @Subscribe
    public void onTaskDelegateCompleted(ReassignTaskEvent event)
    {
        clearFragment();
    }

    private void clearFragment()
    {
        if (DisplayUtils.hasCentralPane(getActivity()))
        {
            FragmentDisplayer.with(getActivity()).remove(TAG);
        }
        else
        {
            getFragmentManager().popBackStack(TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            ((TasksFragment) getFragmentManager().findFragmentByTag(TasksFragment.TAG)).refresh();
        }
    }

    // ///////////////////////////////////////////////////////////////////////////
    // BUILDER
    // ///////////////////////////////////////////////////////////////////////////
    public static Builder with(FragmentActivity activity)
    {
        return new Builder(activity);
    }

    public static class Builder extends LeafFragmentBuilder
    {
        // ///////////////////////////////////////////////////////////////////////////
        // CONSTRUCTORS
        // ///////////////////////////////////////////////////////////////////////////
        public Builder(FragmentActivity activity)
        {
            super(activity);
            this.extraConfiguration = new Bundle();
        }

        public Builder(FragmentActivity appActivity, Map<String, Object> configuration)
        {
            super(appActivity, configuration);
            this.extraConfiguration = new Bundle();
            this.menuIconId = R.drawable.ic_repository_dark;
            this.menuTitleId = R.string.menu_browse_root;
        }

        // ///////////////////////////////////////////////////////////////////////////
        // SETTERS
        // ///////////////////////////////////////////////////////////////////////////
        public Builder process(Process process)
        {
            extraConfiguration.putSerializable(ARGUMENT_PROCESS, process);
            return this;
        }

        public Builder task(Task task)
        {
            extraConfiguration.putSerializable(ARGUMENT_TASK, task);
            return this;
        }

        // ///////////////////////////////////////////////////////////////////////////
        // CREATE FRAGMENT
        // ///////////////////////////////////////////////////////////////////////////
        protected Fragment createFragment(Bundle b)
        {
            return newInstanceByTemplate(b);
        }
    }

    // ///////////////////////////////////////////////////////////////////////////
    // INTERFACE IMPLEMENTATION
    // ///////////////////////////////////////////////////////////////////////////
    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
        // An item was selected. You can retrieve the selected item using
        String SelectedValue =  parent.getItemAtPosition(pos).toString();
        OutcomeChoice OC = null;
        for(int i=0;i<AvailableOutcomes.size(); i++) {
            if (AvailableOutcomes.get(i).DisplayName.equals(SelectedValue)) {
                OC = (OutcomeChoice) AvailableOutcomes.get(i);
                break;
            }
        }

        Spinner ReasonSpinner = (Spinner)vRoot.findViewById(R.id.reason_selection);

        if (OC.RequiresReason)
        {
            ReasonSpinner.setVisibility(View.VISIBLE);
        }
        else
        {
            ReasonSpinner.setVisibility(View.GONE);
        }
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }

    public class OutcomeChoice
    {
        public String DisplayName;
        public String OutcomePropertyName;
        public String OutcomeValue;
        public Boolean RequiresReason;

        public OutcomeChoice(String displayName, String outcomePropertyName, String outcomeValue, Boolean requiresReason)
        {
            this.DisplayName = displayName;
            this.OutcomePropertyName = outcomePropertyName;
            this.OutcomeValue = outcomeValue;
            this.RequiresReason = requiresReason;
        }
    }
}
