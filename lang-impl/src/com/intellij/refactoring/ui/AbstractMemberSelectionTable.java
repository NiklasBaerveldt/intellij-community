package com.intellij.refactoring.ui;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.classMembers.MemberInfoChange;
import com.intellij.refactoring.classMembers.MemberInfoChangeListener;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.RowIcon;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.Table;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public abstract class AbstractMemberSelectionTable<T extends PsiElement, M extends MemberInfoBase<T>> extends Table implements TypeSafeDataProvider {
  protected static final int CHECKED_COLUMN = 0;
  protected static final int DISPLAY_NAME_COLUMN = 1;
  protected static final int ABSTRACT_COLUMN = 2;
  protected static final Icon OVERRIDING_METHOD_ICON = IconLoader.getIcon("/general/overridingMethod.png");
  protected static final Icon IMPLEMENTING_METHOD_ICON = IconLoader.getIcon("/general/implementingMethod.png");
  protected static final Icon EMPTY_OVERRIDE_ICON = new EmptyIcon(16, 16);
  protected final String myAbstractColumnHeader;
  protected static final String DISPLAY_NAME_COLUMN_HEADER = RefactoringBundle.message("member.column");
  protected List<M> myMemberInfos;
  protected final boolean myAbstractEnabled;
  protected MemberInfoModel<T, M> myMemberInfoModel;
  protected MyTableModel<T, M> myTableModel;
  protected static final int OVERRIDE_ICON_POSITION = 2;
  protected static final int VISIBILITY_ICON_POSITION = 1;
  protected static final int MEMBER_ICON_POSITION = 0;

  public AbstractMemberSelectionTable(Collection<M> memberInfos, MemberInfoModel<T, M> memberInfoModel, String abstractColumnHeader) {
    myAbstractEnabled = abstractColumnHeader != null;
    myAbstractColumnHeader = abstractColumnHeader;
    myTableModel = new MyTableModel<T, M>(this);

    myMemberInfos = new ArrayList<M>(memberInfos);
    if (memberInfoModel != null) {
      myMemberInfoModel = memberInfoModel;
    }
    else {
      myMemberInfoModel = new DefaultMemberInfoModel<T, M>();
    }

    setModel(myTableModel);

    TableColumnModel model = getColumnModel();
    model.getColumn(DISPLAY_NAME_COLUMN).setCellRenderer(new MyTableRenderer<T, M>(this));
    model.getColumn(CHECKED_COLUMN).setCellRenderer(new MyBooleanRenderer<T, M>(this));
    final int checkBoxWidth = new JCheckBox().getPreferredSize().width;
    model.getColumn(CHECKED_COLUMN).setMaxWidth(checkBoxWidth);
    model.getColumn(CHECKED_COLUMN).setMinWidth(checkBoxWidth);

    if (myAbstractEnabled) {
      int width =
        (int)(1.3 * getFontMetrics(getFont()).charsWidth(myAbstractColumnHeader.toCharArray(), 0,
                                                             myAbstractColumnHeader.length()));
      model.getColumn(ABSTRACT_COLUMN).setMaxWidth(width);
      model.getColumn(ABSTRACT_COLUMN).setPreferredWidth(width);
      model.getColumn(ABSTRACT_COLUMN).setCellRenderer(new MyBooleanRenderer<T, M>(this));
    }

    setPreferredScrollableViewportSize(new Dimension(400, getRowHeight() * 12));
    getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    setShowGrid(false);
    setIntercellSpacing(new Dimension(0, 0));

    new MyEnableDisableAction().register();
  }

  public Collection<M> getSelectedMemberInfos() {
    ArrayList<M> list = new ArrayList<M>(myMemberInfos.size());
    for (M info : myMemberInfos) {
      if (isMemberInfoSelected(info)) {
//      if (info.isChecked() || (!myMemberInfoModel.isMemberEnabled(info) && myMemberInfoModel.isCheckedWhenDisabled(info))) {
        list.add(info);
      }
    }
    return list;
  }

  private boolean isMemberInfoSelected(final M info) {
    final boolean memberEnabled = myMemberInfoModel.isMemberEnabled(info);
    return (memberEnabled && info.isChecked()) || (!memberEnabled && myMemberInfoModel.isCheckedWhenDisabled(info));
  }

  public MemberInfoModel<T, M> getMemberInfoModel() {
    return myMemberInfoModel;
  }

  public void setMemberInfoModel(MemberInfoModel<T, M> memberInfoModel) {
    myMemberInfoModel = memberInfoModel;
  }

  public void fireExternalDataChange() {
    myTableModel.fireTableDataChanged();
  }

  public void setMemberInfos(Collection<M> memberInfos) {
    myMemberInfos = new ArrayList<M>(memberInfos);
    fireMemberInfoChange(memberInfos);
    myTableModel.fireTableDataChanged();
  }

  public void addMemberInfoChangeListener(MemberInfoChangeListener<T, M> l) {
    listenerList.add(MemberInfoChangeListener.class, l);
  }

  protected void fireMemberInfoChange(Collection<M> changedMembers) {
    Object[] list = listenerList.getListenerList();

    MemberInfoChange event = new MemberInfoChange<T, M>(changedMembers);
    for (Object element : list) {
      if (element instanceof MemberInfoChangeListener) {
        ((MemberInfoChangeListener<T, M>)element).memberInfoChanged(event);
      }
    }
  }

  public void calcData(final DataKey key, final DataSink sink) {
    if (key == LangDataKeys.PSI_ELEMENT) {
      final Collection<M> memberInfos = getSelectedMemberInfos();
      if (memberInfos.size() > 0) {
        sink.put(LangDataKeys.PSI_ELEMENT, memberInfos.iterator().next().getMember());
      }
    }
  }

  public void scrollSelectionInView() {
    for(int i=0; i<myMemberInfos.size(); i++) {
      if (isMemberInfoSelected(myMemberInfos.get(i))) {
        Rectangle rc = getCellRect(i, 0, false);
        scrollRectToVisible(rc);
        break;
      }
    }
  }

  public void addNotify() {
    super.addNotify();
    scrollSelectionInView();
  }

  protected abstract Object getAbstractColumnValue(M memberInfo);

  protected abstract boolean isAbstractColumnEditable(int rowIndex);

  protected abstract void setVisibilityIcon(M memberInfo, RowIcon icon);

  protected abstract Icon getOverrideIcon(M memberInfo);

  private static class DefaultMemberInfoModel<T extends PsiElement, M extends MemberInfoBase<T>> implements MemberInfoModel<T, M> {
    public boolean isMemberEnabled(M member) {
      return true;
    }

    public boolean isCheckedWhenDisabled(M member) {
      return false;
    }

    public boolean isAbstractEnabled(M member) {
      return true;
    }

    public boolean isAbstractWhenDisabled(M member) {
      return false;
    }


    public int checkForProblems(@NotNull M member) {
      return OK;
    }

    public void memberInfoChanged(MemberInfoChange<T, M> event) {
    }

    public Boolean isFixedAbstract(M member) {
      return null;
    }

    public String getTooltipText(M member) {
      return null;
    }
  }

  private static class MyTableModel<T extends PsiElement, M extends MemberInfoBase<T>> extends AbstractTableModel {
    private final AbstractMemberSelectionTable<T, M> myTable;

    public MyTableModel(AbstractMemberSelectionTable<T, M> table) {
      myTable = table;
    }

    public int getColumnCount() {
      if (myTable.myAbstractEnabled) {
        return 3;
      }
      else {
        return 2;
      }
    }

    public int getRowCount() {
      return myTable.myMemberInfos.size();
    }

    public Class getColumnClass(int columnIndex) {
      if (columnIndex == CHECKED_COLUMN || columnIndex == ABSTRACT_COLUMN) {
        return Boolean.class;
      }
      return super.getColumnClass(columnIndex);
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      final M memberInfo = myTable.myMemberInfos.get(rowIndex);
      switch (columnIndex) {
        case CHECKED_COLUMN:
          if (myTable.myMemberInfoModel.isMemberEnabled(memberInfo)) {
            return memberInfo.isChecked() ? Boolean.TRUE : Boolean.FALSE;
          }
          else {
            return myTable.myMemberInfoModel.isCheckedWhenDisabled(memberInfo);
          }
        case ABSTRACT_COLUMN:
          {
            return myTable.getAbstractColumnValue(memberInfo);
          }
        case DISPLAY_NAME_COLUMN:
          return memberInfo.getDisplayName();
        default:
          throw new RuntimeException("Incorrect column index");
      }
    }

    public String getColumnName(int column) {
      switch (column) {
        case CHECKED_COLUMN:
          return " ";
        case ABSTRACT_COLUMN:
          return myTable.myAbstractColumnHeader;
        case DISPLAY_NAME_COLUMN:
          return DISPLAY_NAME_COLUMN_HEADER;
        default:
          throw new RuntimeException("Incorrect column index");
      }
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      switch (columnIndex) {
        case CHECKED_COLUMN:
          return myTable.myMemberInfoModel.isMemberEnabled(myTable.myMemberInfos.get(rowIndex));
        case ABSTRACT_COLUMN:
          return myTable.isAbstractColumnEditable(rowIndex);
      }
      return false;
    }


    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
      if (columnIndex == CHECKED_COLUMN) {
        myTable.myMemberInfos.get(rowIndex).setChecked(((Boolean)aValue).booleanValue());
      }
      else if (columnIndex == ABSTRACT_COLUMN) {
        myTable.myMemberInfos.get(rowIndex).setToAbstract(((Boolean)aValue).booleanValue());
      }

      Collection<M> changed = Collections.singletonList(myTable.myMemberInfos.get(rowIndex));
      myTable.fireMemberInfoChange(changed);
      fireTableDataChanged();
//      fireTableRowsUpdated(rowIndex, rowIndex);
    }
  }

  private class MyEnableDisableAction extends EnableDisableAction {

    protected JTable getTable() {
      return AbstractMemberSelectionTable.this;
    }

    protected void applyValue(int[] rows, boolean valueToBeSet) {
      List<M> changedInfo = new ArrayList<M>();
      for (int row : rows) {
        final M memberInfo = myMemberInfos.get(row);
        memberInfo.setChecked(valueToBeSet);
        changedInfo.add(memberInfo);
      }
      fireMemberInfoChange(changedInfo);
      final int selectedRow = getSelectedRow();
      myTableModel.fireTableDataChanged();
      setRowSelectionInterval(selectedRow, selectedRow);
    }

    protected boolean isRowChecked(final int row) {
      return myMemberInfos.get(row).isChecked();
    }
  }

  private static class MyTableRenderer<T extends PsiElement, M extends MemberInfoBase<T>> extends ColoredTableCellRenderer {
    private final AbstractMemberSelectionTable<T, M> myTable;

    public MyTableRenderer(AbstractMemberSelectionTable<T, M> table) {
      myTable = table;
    }

    public void customizeCellRenderer(JTable table, final Object value,
                                      boolean isSelected, boolean hasFocus, final int row, final int column) {

      final int modelColumn = myTable.convertColumnIndexToModel(column);
      final M memberInfo = myTable.myMemberInfos.get(row);
      setToolTipText(myTable.myMemberInfoModel.getTooltipText(memberInfo));
      T member = memberInfo.getMember();
      switch (modelColumn) {
        case DISPLAY_NAME_COLUMN:
          {
            Icon memberIcon = member.getIcon(MEMBER_ICON_POSITION);
            Icon overrideIcon = myTable.getOverrideIcon(memberInfo);

            RowIcon icon = new RowIcon(3);
            icon.setIcon(memberIcon, MEMBER_ICON_POSITION);
            myTable.setVisibilityIcon(memberInfo, icon);
            icon.setIcon(overrideIcon, OVERRIDE_ICON_POSITION);
            setIcon(icon);
            break;
          }
        default:
          {
            setIcon(null);
          }
      }
      final boolean cellEditable = myTable.myMemberInfoModel.isMemberEnabled(memberInfo);
      setEnabled(cellEditable);

      if (value == null) return;
      final int problem = myTable.myMemberInfoModel.checkForProblems(memberInfo);
      Color c = null;
      if (problem == MemberInfoModel.ERROR) {
        c = Color.red;
      }
      else if (problem == MemberInfoModel.WARNING && !isSelected) {
        c = Color.blue;
      }
      append((String)value, new SimpleTextAttributes(Font.PLAIN, c));
    }

  }

  private static class MyBooleanRenderer<T extends PsiElement, M extends MemberInfoBase<T>> extends BooleanTableCellRenderer {
    private final AbstractMemberSelectionTable<T, M> myTable;

    public MyBooleanRenderer(AbstractMemberSelectionTable<T, M> table) {
      myTable = table;
    }

    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
      Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (component instanceof JComponent) {
        JComponent jComponent = (JComponent)component;
        int modelColumn = myTable.convertColumnIndexToModel(column);
        final M memberInfo = myTable.myMemberInfos.get(row);

        jComponent.setEnabled(
          (modelColumn == CHECKED_COLUMN && myTable.myMemberInfoModel.isMemberEnabled(memberInfo)
           || (modelColumn == ABSTRACT_COLUMN && memberInfo.isChecked() && myTable.isAbstractColumnEditable(row)))
        );
      }
      return component;
    }
  }
}
