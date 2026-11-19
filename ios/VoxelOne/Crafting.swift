import UIKit

/// One recipe transaction per tap. The controller owns inventory, persistence and authority.
@MainActor final class NativeCraftingScreen:UIViewController,UITableViewDataSource,UITableViewDelegate,UISearchBarDelegate {
    private let recipes:[NativeRecipe]
    private let name:(Int)->String
    private let search=UISearchBar()
    private let table=UITableView(frame:.zero,style:.plain)
    private let status=UILabel()
    private let category=UISegmentedControl(items:["All","Materials","Build","Decor","Light"])
    private var shown:[NativeRecipe]=[]
    private var inventory:[[Int]]=[]
    private var pending=false
    var onCraft:((NativeRecipe)->Void)?
    var onClose:(()->Void)?
    init(recipes:[NativeRecipe],name:@escaping (Int)->String){self.recipes=recipes;self.name=name;super.init(nibName:nil,bundle:nil)}
    required init?(coder:NSCoder){fatalError("init(coder:) has not been implemented")}
    override func viewDidLoad(){
        super.viewDidLoad();view.backgroundColor = .systemBackground
        let title=UILabel();title.text="Craft";title.font = .preferredFont(forTextStyle:.title2)
        let done=UIButton(type:.system);done.setTitle("Done",for:.normal);done.accessibilityIdentifier="craftDone";done.addTarget(self,action:#selector(close),for:.touchUpInside)
        let header=UIStackView(arrangedSubviews:[title,done]);header.distribution = .equalSpacing
        search.placeholder="Find recipe or ingredient";search.accessibilityIdentifier="craftSearchBar";search.searchTextField.accessibilityIdentifier="craftSearch";search.delegate=self
        category.selectedSegmentIndex=0;category.addTarget(self,action:#selector(filter),for:.valueChanged);category.accessibilityIdentifier="craftCategory"
        status.numberOfLines=0;status.font = .preferredFont(forTextStyle:.footnote);status.accessibilityIdentifier="craftStatus"
        table.dataSource=self;table.delegate=self;table.accessibilityIdentifier="recipeTable";table.keyboardDismissMode = .onDrag
        table.rowHeight=UITableView.automaticDimension;table.estimatedRowHeight=76
        let stack=UIStackView(arrangedSubviews:[header,search,category,status,table]);stack.axis = .vertical;stack.spacing=8;stack.translatesAutoresizingMaskIntoConstraints=false
        view.addSubview(stack);let safe=view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo:safe.leadingAnchor,constant:12),stack.trailingAnchor.constraint(equalTo:safe.trailingAnchor,constant:-12),stack.topAnchor.constraint(equalTo:safe.topAnchor,constant:8),stack.bottomAnchor.constraint(equalTo:view.keyboardLayoutGuide.topAnchor,constant:-8),done.heightAnchor.constraint(greaterThanOrEqualToConstant:44),category.heightAnchor.constraint(greaterThanOrEqualToConstant:36)])
        filter()
    }
    func update(inventory:[[Int]],message:String,pending:Bool){
        loadViewIfNeeded();status.text=message;table.isUserInteractionEnabled = !pending
        guard self.inventory != inventory || self.pending != pending else{return}
        self.inventory=inventory;self.pending=pending;table.reloadData()
    }
    @objc private func close(){view.endEditing(true);let finish=onClose;dismiss(animated:true){finish?()}}
    @objc private func filter(){
        let query=(search.text ?? "").trimmingCharacters(in:.whitespacesAndNewlines)
        let chosen=["All","Materials","Building","Decorations","Lighting"][category.selectedSegmentIndex]
        shown=recipes.filter { recipe in
            let text=([recipe.name,name(recipe.output)]+recipe.inputs.map{name($0[0])}).joined(separator:" ")
            return (chosen=="All" || recipe.category==chosen) && (query.isEmpty || text.localizedCaseInsensitiveContains(query))
        }
        let empty=UILabel();empty.text="No matching recipes.";empty.textAlignment = .center;empty.textColor = .secondaryLabel
        table.backgroundView=shown.isEmpty ? empty:nil;table.reloadData()
    }
    func searchBar(_ searchBar:UISearchBar,textDidChange searchText:String){filter()}
    func tableView(_ tableView:UITableView,numberOfRowsInSection section:Int)->Int {shown.count}
    func tableView(_ tableView:UITableView,cellForRowAt indexPath:IndexPath)->UITableViewCell {
        let cell=tableView.dequeueReusableCell(withIdentifier:"recipe") ?? UITableViewCell(style:.subtitle,reuseIdentifier:"recipe")
        let recipe=shown[indexPath.row]
        cell.textLabel?.text="\(recipe.name) → \(recipe.count) \(name(recipe.output))";cell.textLabel?.numberOfLines=0
        cell.detailTextLabel?.text=recipe.inputs.map { item in
            let owned=inventory.filter{$0[0]==item[0]}.reduce(0){$0+$1[1]}
            return "\(name(item[0])) \(owned)/\(item[1])"
        }.joined(separator:" · ");cell.detailTextLabel?.numberOfLines=0
        cell.accessibilityIdentifier="recipe\(recipe.id)";cell.accessoryType = .disclosureIndicator
        cell.textLabel?.textColor = pending ? .secondaryLabel:.label
        return cell
    }
    func tableView(_ tableView:UITableView,didSelectRowAt indexPath:IndexPath){
        tableView.deselectRow(at:indexPath,animated:true);guard !pending else{return};view.endEditing(true);onCraft?(shown[indexPath.row])
    }
}
